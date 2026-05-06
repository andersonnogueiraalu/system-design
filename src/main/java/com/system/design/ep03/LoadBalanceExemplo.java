package com.system.design.ep03;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**

 - Ep.03 — Load Balancer: distribuindo carga com inteligência
 - Série: System Design que TODO dev Java precisa dominar
 -
 - Implementa do zero as 3 principais estratégias de Load Balancing:
 -
 - 1. Round Robin         → distribui em sequência circular          O(1)
 - 1. Least Connections   → envia para o servidor menos ocupado      O(n)
 - 1. Weighted Round Robin → respeita a capacidade de cada servidor  O(n)
 -
 - Padrão usado: Strategy — cada algoritmo é intercambiável em runtime.
 - (Design Patterns será tema de uma próxima série!)
 -
 - @author Anderson Nogueira
 - @see <a href="https://linkedin.com/in/anderson-nogueira">LinkedIn</a>
 */
public class LoadBalanceExemplo {

    // ─────────────────────────────────────────────────────────────────────────
    // SERVER — representa uma instância do serviço
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Representa um servidor no pool do Load Balancer.
     *
     * @param id         identificador único
     * @param weight     capacidade relativa (usado no Weighted Round Robin)
     */
    static class Server {
        private final String id;
        private final int weight;
        private final AtomicInteger activeConnections = new AtomicInteger(0);
        private final AtomicInteger totalHandled     = new AtomicInteger(0);
        private boolean healthy = true;

        public Server(String id, int weight) {
            this.id     = id;
            this.weight = weight;
        }

        /** Simula início do processamento de uma requisição. O(1) */
        public void connect()    { activeConnections.incrementAndGet(); totalHandled.incrementAndGet(); }

        /** Simula fim do processamento de uma requisição. O(1) */
        public void disconnect() { activeConnections.decrementAndGet(); }

        public String getId()             { return id; }
        public int    getWeight()         { return weight; }
        public int    getActiveConns()    { return activeConnections.get(); }
        public int    getTotalHandled()   { return totalHandled.get(); }
        public boolean isHealthy()        { return healthy; }
        public void   setHealthy(boolean h) { this.healthy = h; }

        @Override public String toString() {
            return String.format("Server[%s | peso:%d | ativas:%d | total:%d | %s]",
                    id, weight, getActiveConns(), getTotalHandled(),
                    healthy ? "UP" : "DOWN");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STRATEGY — interface de algoritmo de balanceamento
    // ─────────────────────────────────────────────────────────────────────────

    /** Contrato comum para todas as estratégias de balanceamento. */
    interface BalancingStrategy {
        /**
         * Seleciona o próximo servidor saudável para receber a requisição.
         *
         * @param servers lista de servidores disponíveis no pool
         * @return servidor escolhido, ou Optional.empty() se todos indisponíveis
         */
        Optional<Server> next(List<Server> servers);
        String name();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ESTRATÉGIA 1 — Round Robin
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Distribui requisições em sequência circular entre os servidores saudáveis.
     *
     * Vantagem: simples, sem overhead.
     * Desvantagem: ignora o estado real de cada servidor.
     *
     * Complexidade: O(n) no pior caso (procurando servidor saudável)
     */
    static class RoundRobinStrategy implements BalancingStrategy {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Optional<Server> next(List<Server> servers) {
            List<Server> healthy = servers.stream().filter(Server::isHealthy).toList();
            if (healthy.isEmpty()) return Optional.empty();
            int index = counter.getAndIncrement() % healthy.size();
            return Optional.of(healthy.get(index));
        }

        @Override public String name() { return "Round Robin"; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ESTRATÉGIA 2 — Least Connections
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Envia a requisição para o servidor com menos conexões ativas no momento.
     *
     * Vantagem: distribui melhor quando requisições têm tempos variados.
     * Desvantagem: requer monitoramento de conexões ativas em tempo real.
     *
     * Complexidade: O(n) — percorre todos os servidores para encontrar o mínimo
     */
    static class LeastConnectionsStrategy implements BalancingStrategy {

        @Override
        public Optional<Server> next(List<Server> servers) {
            return servers.stream()
                    .filter(Server::isHealthy)
                    .min(Comparator.comparingInt(Server::getActiveConns));
        }

        @Override public String name() { return "Least Connections"; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ESTRATÉGIA 3 — Weighted Round Robin
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Expande o pool proporcionalmente ao peso de cada servidor.
     * Servidor com peso 3 aparece 3x mais vezes na fila → recebe 3x mais tráfego.
     *
     * Vantagem: respeita a capacidade real de hardware diferente.
     * Desvantagem: maior uso de memória para montar a fila expandida.
     *
     * Complexidade: O(n * w) para montar a fila, O(1) por requisição
     *   onde n = número de servidores, w = peso médio
     */
    static class WeightedRoundRobinStrategy implements BalancingStrategy {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Optional<Server> next(List<Server> servers) {
            List<Server> pool = new ArrayList<>();
            for (Server s : servers) {
                if (s.isHealthy()) {
                    for (int i = 0; i < s.getWeight(); i++) pool.add(s);
                }
            }
            if (pool.isEmpty()) return Optional.empty();
            int index = counter.getAndIncrement() % pool.size();
            return Optional.of(pool.get(index));
        }

        @Override public String name() { return "Weighted Round Robin"; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD BALANCER — orquestra servidores e estratégia
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Load Balancer com Health Check e troca de estratégia em runtime.
     *
     * Responsabilidades:
     *   → Manter pool de servidores
     *   → Rotear requisições via estratégia configurável
     *   → Detectar servidores indisponíveis (health check simulado)
     */
    static class LoadBalancer {
        private final List<Server>    servers  = new ArrayList<>();
        private       BalancingStrategy strategy;

        public LoadBalancer(BalancingStrategy strategy) {
            this.strategy = strategy;
        }

        /** Registra servidor no pool. O(1) amortizado */
        public void addServer(Server server) {
            servers.add(server);
        }

        /** Troca de estratégia em runtime sem downtime. O(1) */
        public void setStrategy(BalancingStrategy strategy) {
            this.strategy = strategy;
            System.out.printf("  🔄 Estratégia alterada para: %s%n", strategy.name());
        }

        /**
         * Roteia uma requisição para o servidor selecionado pela estratégia.
         * Simula processamento e libera a conexão após conclusão.
         *
         * @param requestId identificador da requisição
         */
        public void route(int requestId) {
            Optional<Server> chosen = strategy.next(servers);
            if (chosen.isEmpty()) {
                System.out.printf("  ❌ Req #%d REJEITADA — nenhum servidor disponível%n", requestId);
                return;
            }
            Server s = chosen.get();
            s.connect();
            System.out.printf("  → Req #%-3d  %-22s  [%s]%n", requestId, strategy.name(), s.getId());
            s.disconnect(); // em real: liberado após resposta HTTP
        }

        public void printStats() {
            System.out.println("\n  📊 Status do pool:");
            servers.forEach(s -> System.out.printf("     %s%n", s));
        }

        /** Simula health check: marca servidor como DOWN. */
        public void markDown(String serverId) {
            servers.stream()
                    .filter(s -> s.getId().equals(serverId))
                    .findFirst()
                    .ifPresent(s -> {
                        s.setHealthy(false);
                        System.out.printf("  🔴 Health check falhou: %s está DOWN%n", serverId);
                    });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEMONSTRAÇÃO
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("  Ep.03 — Load Balancer: distribuindo carga");
        System.out.println("  System Design que TODO dev Java precisa dominar");
        System.out.println("=".repeat(60));

        Server s1 = new Server("server-A", 1);
        Server s2 = new Server("server-B", 1);
        Server s3 = new Server("server-C", 2); // peso maior → mais capacidade

        LoadBalancer lb = new LoadBalancer(new RoundRobinStrategy());
        lb.addServer(s1);
        lb.addServer(s2);
        lb.addServer(s3);

        // ── Round Robin ──
        System.out.println("\n⚖️  Round Robin — distribuição sequencial");
        System.out.println("-".repeat(50));
        for (int i = 1; i <= 6; i++) lb.route(i);

        // ── Least Connections ──
        System.out.println("\n📉  Least Connections — menor carga primeiro");
        System.out.println("-".repeat(50));
        lb.setStrategy(new LeastConnectionsStrategy());
        // Simula server-A ocupado antes de rotear
        s1.connect(); s1.connect();
        for (int i = 7; i <= 12; i++) lb.route(i);
        s1.disconnect(); s1.disconnect();

        // ── Weighted Round Robin ──
        System.out.println("\n🏋️  Weighted Round Robin — respeitando capacidade");
        System.out.println("-".repeat(50));
        lb.setStrategy(new WeightedRoundRobinStrategy());
        for (int i = 13; i <= 18; i++) lb.route(i);

        // ── Health Check ──
        System.out.println("\n🚨  Health Check — simulando falha de servidor");
        System.out.println("-".repeat(50));
        lb.setStrategy(new RoundRobinStrategy());
        lb.markDown("server-B");
        for (int i = 19; i <= 22; i++) lb.route(i);

        lb.printStats();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  Próximo: Ep.04 — Cache com Redis e estratégias de invalidação");
        System.out.println("=".repeat(60));
    }
}