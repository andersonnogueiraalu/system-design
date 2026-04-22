package com.system.design.ep02;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 * Ep.02 — Escalabilidade: Vertical vs Horizontal
 * Série: System Design que dev Java precisa dominar
 *
 * Demonstra na prática a diferença entre Scale Up e Scale Out:
 *
 * Scale Up   → aumentar recursos de uma única instância (ThreadPool com mais threads)
 * Scale Out  → adicionar instâncias independentes (múltiplos workers stateless)
 *
 * Conceito-chave: para Scale Out funcionar, o serviço precisa ser STATELESS.
 *
 * @author Anderson Nogueira
 * @see <a href="https://linkedin.com/in/anderson-freitas21">LinkedIn</a>
 */

public class ScalabilityExemplo {
    // ─────────────────────────────────────────────────────────────────────────
    // SCALE UP — Um único servidor com mais capacidade (mais threads = mais CPU)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Simula um servidor com capacidade fixa (Scale Up).
     * A capacidade máxima é limitada pelo hardware — tem um teto.
     *
     * Throughput: O(n) onde n = número de requisições
     * Latência por req: O(1) enquanto abaixo da capacidade máxima
     */
    static class VerticalServer {
        private final String name;
        private final ExecutorService threadPool;
        private final int maxCapacity;
        private final AtomicInteger processedRequests = new AtomicInteger(0);

        /**
         * @param name        identificador do servidor
         * @param maxCapacity número máximo de threads simultâneas (simula limite de hardware)
         */
        public VerticalServer(String name, int maxCapacity) {
            this.name = name;
            this.maxCapacity = maxCapacity;
            this.threadPool = new ThreadPoolExecutor(
                    maxCapacity, maxCapacity,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(maxCapacity), // fila limitada — simula teto do hardware
                    new ThreadPoolExecutor.AbortPolicy()    // rejeita quando cheio
            );
        }

        /**
         * Processa uma requisição. Lança RejectedExecutionException se acima da capacidade.
         *
         * @param requestId identificador da requisição
         */
        public void handle(int requestId) {
            threadPool.submit(() -> {
                try {
                    Thread.sleep(50); // simula processamento
                    processedRequests.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        public void shutdown() throws InterruptedException {
            threadPool.shutdown();
            threadPool.awaitTermination(5, TimeUnit.SECONDS);
        }

        public int getProcessed() { return processedRequests.get(); }
        public String getName()   { return name; }
        public int getCapacity()  { return maxCapacity; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SCALE OUT — Múltiplas instâncias stateless distribuindo a carga
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Representa uma instância stateless de serviço.
     * Stateless = não guarda estado entre requisições → pode ser replicada livremente.
     *
     * Cada instância processa de forma independente.
     * O estado compartilhado (ex: sessão) deve ir para Redis ou banco externo.
     */
    static class StatelessServiceInstance {
        private final String instanceId;
        private final AtomicInteger requestCount = new AtomicInteger(0);

        public StatelessServiceInstance(String instanceId) {
            this.instanceId = instanceId;
        }

        /**
         * Processa uma requisição sem guardar estado local.
         * Complexidade: O(1) por requisição
         *
         * @param requestId identificador da requisição
         * @return resultado do processamento
         */
        public String process(int requestId) {
            requestCount.incrementAndGet();
            return String.format("[%s] Requisição #%d processada", instanceId, requestId);
        }

        public int getRequestCount() { return requestCount.get(); }
        public String getInstanceId() { return instanceId; }
    }

    /**
     * Cluster horizontal: conjunto de instâncias stateless.
     * Simula o Scale Out — adicionamos instâncias conforme a demanda cresce.
     *
     * Throughput total: O(n * i) onde n = capacidade por instância, i = número de instâncias
     * Adicionar instância: O(1) — sem impacto nas demais
     */
    static class HorizontalCluster {
        private final List<StatelessServiceInstance> instances = new ArrayList<>();
        private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

        public void addInstance(String instanceId) {
            instances.add(new StatelessServiceInstance(instanceId));
            System.out.printf("  ✅ Instância '%s' adicionada ao cluster (total: %d)%n",
                    instanceId, instances.size());
        }

        /**
         * Distribui requisição para a próxima instância (Round Robin simples).
         * O Load Balancer completo será implementado no Ep.03!
         *
         * Complexidade: O(1)
         *
         * @param requestId identificador da requisição
         */
        public String dispatch(int requestId) {
            if (instances.isEmpty()) throw new IllegalStateException("Cluster sem instâncias!");
            int index = roundRobinIndex.getAndIncrement() % instances.size();
            return instances.get(index).process(requestId);
        }

        public void printStats() {
            System.out.println("\n  📊 Distribuição de carga entre instâncias:");
            instances.forEach(i ->
                    System.out.printf("     %s → %d requisições%n",
                            i.getInstanceId(), i.getRequestCount())
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEMONSTRAÇÃO
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=".repeat(60));
        System.out.println("  Ep.02 — Escalabilidade: Vertical vs Horizontal");
        System.out.println("  System Design que TODO dev Java precisa dominar");
        System.out.println("=".repeat(60));

        // ── SCALE UP ──
        System.out.println("\n⬆️  SCALE UP — Servidor único com capacidade aumentada");
        System.out.println("-".repeat(50));

        VerticalServer serverV1 = new VerticalServer("server-v1", 5);
        VerticalServer serverV2 = new VerticalServer("server-v2", 20); // upgrade

        System.out.printf("  Antes do upgrade: capacidade = %d threads%n", serverV1.getCapacity());
        System.out.printf("  Após  o upgrade:  capacidade = %d threads%n", serverV2.getCapacity());
        System.out.println("  ⚠️  Teto físico existe. Custo cresce exponencialmente.");
        System.out.println("  ⚠️  Ponto único de falha — se cair, tudo cai.");

        serverV1.shutdown();
        serverV2.shutdown();

        // ── SCALE OUT ──
        System.out.println("\n↔️  SCALE OUT — Cluster crescendo sob demanda");
        System.out.println("-".repeat(50));

        HorizontalCluster cluster = new HorizontalCluster();

        System.out.println("\n  Fase 1 — Tráfego normal:");
        cluster.addInstance("instance-A");
        cluster.addInstance("instance-B");
        for (int i = 1; i <= 6; i++) cluster.dispatch(i);
        cluster.printStats();

        System.out.println("\n  Fase 2 — Pico de tráfego (ex: Black Friday):");
        cluster.addInstance("instance-C"); // escala horizontalmente
        for (int i = 7; i <= 15; i++) cluster.dispatch(i);
        cluster.printStats();

        System.out.println("\n  ✅ Nenhuma instância reiniciada.");
        System.out.println("  ✅ Sem downtime. Sem teto físico.");
        System.out.println("  ✅ Load Balancer será o próximo passo — Ep.03!\n");

        System.out.println("=".repeat(60));
        System.out.println("  Regra de ouro: aplicação stateless → escala horizontal livre");
        System.out.println("  Sessão de usuário? → Redis. JWT? → stateless por natureza.");
        System.out.println("=".repeat(60));
    }
}
