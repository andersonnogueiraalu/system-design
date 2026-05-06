package com.system.design.ep04;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ep.04 — Cache: estratégias de leitura e invalidação
 * Série: System Design que TODO dev Java precisa dominar
 *
 * Implementa do zero as principais estratégias de cache:
 *
 *   1. Cache-Aside (Lazy Loading)  → app controla leitura/escrita no cache  O(1)
 *   2. Write-Through               → escreve no cache e no banco de forma síncrona
 *   3. Write-Behind (Write-Back)   → escreve no cache e sincroniza o banco depois
 *
 * Política de invalidação: LRU (Least Recently Used) com LinkedHashMap — O(1)
 *
 * Em produção: substitua o "banco simulado" por JPA/Hibernate
 *              e o "cache simulado" por RedisTemplate (Spring Data Redis)
 *
 * @author Anderson Nogueira
 */
public class CacheExemplo {

    // ── MÉTRICAS ──────────────────────────────────────────────────────────────

    static class CacheMetrics {
        private final AtomicInteger hits     = new AtomicInteger(0);
        private final AtomicInteger misses   = new AtomicInteger(0);
        private final AtomicInteger dbReads  = new AtomicInteger(0);
        private final AtomicInteger dbWrites = new AtomicInteger(0);

        public void hit()     { hits.incrementAndGet(); }
        public void miss()    { misses.incrementAndGet(); }
        public void dbRead()  { dbReads.incrementAndGet(); }
        public void dbWrite() { dbWrites.incrementAndGet(); }

        /** Hit Rate: meta de producao > 90% para workloads de leitura */
        public double hitRate() {
            int total = hits.get() + misses.get();
            return total == 0 ? 0.0 : (double) hits.get() / total * 100;
        }

        public void print(String name) {
            System.out.printf("%n  [Metricas - %s]%n", name);
            System.out.printf("  Hits: %d | Misses: %d | Hit Rate: %.1f%%%n",
                    hits.get(), misses.get(), hitRate());
            System.out.printf("  DB Reads: %d | DB Writes: %d%n",
                    dbReads.get(), dbWrites.get());
        }
    }

    // ── BANCO SIMULADO ────────────────────────────────────────────────────────

    static class Database {
        private final Map<String, String> storage = new HashMap<>();
        private final CacheMetrics metrics;

        public Database(CacheMetrics metrics) {
            this.metrics = metrics;
            storage.put("user:1", "Anderson Nogueira");
            storage.put("user:2", "Maria Silva");
            storage.put("user:3", "Carlos Souza");
        }

        /** Complexidade: O(1) com indice; simula latencia alta do banco real */
        public Optional<String> read(String key) {
            metrics.dbRead();
            System.out.printf("    [DB READ]  key='%s'%n", key);
            return Optional.ofNullable(storage.get(key));
        }

        /** Complexidade: O(1) amortizado */
        public void write(String key, String value) {
            metrics.dbWrite();
            storage.put(key, value);
            System.out.printf("    [DB WRITE] key='%s' value='%s'%n", key, value);
        }
    }

    // ── CACHE LRU ─────────────────────────────────────────────────────────────

    /**
     * Cache com politica LRU (Least Recently Used).
     *
     * LinkedHashMap com accessOrder=true:
     *  - get() move o item para o fim (mais recente)
     *  - removeEldestEntry() expulsa o head (menos recente) quando cheio
     *
     * Complexidade: get O(1) | put O(1) | evict O(1)
     *
     * Redis equivalente: maxmemory-policy allkeys-lru
     */
    static class LRUCache {
        private final int capacity;
        private final Map<String, String> store;
        private final CacheMetrics metrics;

        public LRUCache(int capacity, CacheMetrics metrics) {
            this.capacity = capacity;
            this.metrics  = metrics;
            this.store = Collections.synchronizedMap(
                    new LinkedHashMap<>(capacity, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                            boolean evict = size() > capacity;
                            if (evict)
                                System.out.printf("    [LRU EVICT] key='%s' removido%n", eldest.getKey());
                            return evict;
                        }
                    }
            );
        }

        public Optional<String> get(String key) {
            String value = store.get(key);
            if (value != null) {
                metrics.hit();
                System.out.printf("    [CACHE HIT]  key='%s' -> '%s'%n", key, value);
                return Optional.of(value);
            }
            metrics.miss();
            System.out.printf("    [CACHE MISS] key='%s'%n", key);
            return Optional.empty();
        }

        public void put(String key, String value) {
            store.put(key, value);
            System.out.printf("    [CACHE SET]  key='%s' value='%s'%n", key, value);
        }

        public void invalidate(String key) {
            store.remove(key);
            System.out.printf("    [INVALIDATE] key='%s'%n", key);
        }

        public int size() { return store.size(); }
    }

    // ── ESTRATEGIA 1: Cache-Aside ─────────────────────────────────────────────

    /**
     * A APLICACAO gerencia o cache manualmente.
     *
     * Leitura:  1) busca no cache -> 2) miss: busca no banco e popula cache
     * Escrita:  1) escreve no banco -> 2) invalida cache (evita stale data)
     *
     * Vantagem:  cache so armazena dados realmente acessados (lazy)
     * Desvantagem: cold start lento (primeiro acesso sempre vai ao banco)
     *
     * Padrao dominante com Spring + Redis em sistemas brasileiros (Nubank, iFood)
     */
    static class CacheAsideStrategy {
        private final LRUCache cache;
        private final Database db;

        public CacheAsideStrategy(LRUCache cache, Database db) {
            this.cache = cache; this.db = db;
        }

        public Optional<String> read(String key) {
            Optional<String> cached = cache.get(key);
            if (cached.isPresent()) return cached;
            Optional<String> fromDb = db.read(key);
            fromDb.ifPresent(v -> cache.put(key, v));
            return fromDb;
        }

        public void write(String key, String value) {
            db.write(key, value);
            cache.invalidate(key); // proxima leitura busca do banco (fresh)
        }
    }

    // ── ESTRATEGIA 2: Write-Through ───────────────────────────────────────────

    /**
     * Toda escrita passa pelo cache E pelo banco de forma SINCRONA.
     *
     * Escrita:  1) cache.put() -> 2) db.write() -> 3) retorna confirmacao
     *
     * Vantagem:  cache sempre consistente com o banco
     * Desvantagem: latencia de escrita maior (duas operacoes sincronas)
     *
     * Ideal para: sistemas financeiros, dados de pedido, carrinho de compras
     */
    static class WriteThroughStrategy {
        private final LRUCache cache;
        private final Database db;

        public WriteThroughStrategy(LRUCache cache, Database db) {
            this.cache = cache; this.db = db;
        }

        public Optional<String> read(String key) {
            Optional<String> cached = cache.get(key);
            if (cached.isPresent()) return cached;
            Optional<String> fromDb = db.read(key);
            fromDb.ifPresent(v -> cache.put(key, v));
            return fromDb;
        }

        public void write(String key, String value) {
            cache.put(key, value); // cache primeiro
            db.write(key, value);  // banco sincrono em seguida
        }
    }

    // ── ESTRATEGIA 3: Write-Behind ────────────────────────────────────────────

    /**
     * Escreve no cache IMEDIATAMENTE; banco sincronizado de forma ASSINCRONA.
     *
     * Escrita:  1) cache.put() -> retorna OK | 2) fila -> flush para banco depois
     *
     * Vantagem:  latencia de escrita minima para o cliente
     * Desvantagem: janela de perda de dados se o cache cair antes do flush
     *
     * Ideal para: contadores, visualizacoes, analytics de alta frequencia
     * Redis pattern: Redis Streams + consumer group para o flush assincrono
     */
    static class WriteBehindStrategy {
        private final LRUCache cache;
        private final Database db;
        private final Queue<Map.Entry<String, String>> writeQueue = new ConcurrentLinkedQueue<>();

        public WriteBehindStrategy(LRUCache cache, Database db) {
            this.cache = cache; this.db = db;
        }

        public Optional<String> read(String key) {
            Optional<String> cached = cache.get(key);
            if (cached.isPresent()) return cached;
            Optional<String> fromDb = db.read(key);
            fromDb.ifPresent(v -> cache.put(key, v));
            return fromDb;
        }

        public void write(String key, String value) {
            cache.put(key, value);
            writeQueue.offer(Map.entry(key, value));
            System.out.printf("    [WRITE QUEUE] '%s' enfileirado (flush pendente)%n", key);
        }

        /** Simula flush assincrono em batch — producao: scheduler ou Kafka consumer */
        public void flush() {
            System.out.printf("    [FLUSH] Sincronizando %d item(ns) com o banco...%n", writeQueue.size());
            while (!writeQueue.isEmpty()) {
                Map.Entry<String, String> entry = writeQueue.poll();
                db.write(entry.getKey(), entry.getValue());
            }
        }

        public int pendingWrites() { return writeQueue.size(); }
    }

    // ── DEMO ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("  Ep.04 - Cache: estrategias e invalidacao");
        System.out.println("  System Design que TODO dev Java precisa dominar");
        System.out.println("=".repeat(60));

        // Cache-Aside
        System.out.println("\n[1] CACHE-ASIDE");
        System.out.println("-".repeat(50));
        CacheMetrics m1 = new CacheMetrics();
        CacheAsideStrategy aside = new CacheAsideStrategy(new LRUCache(3, m1), new Database(m1));

        System.out.println("  1a leitura - cache frio:");
        aside.read("user:1");
        System.out.println("  2a leitura - cache quente:");
        aside.read("user:1");
        System.out.println("  Escrita + invalidacao:");
        aside.write("user:1", "Anderson (atualizado)");
        System.out.println("  3a leitura - pos-invalidacao:");
        aside.read("user:1");
        m1.print("Cache-Aside");

        // Write-Through
        System.out.println("\n[2] WRITE-THROUGH");
        System.out.println("-".repeat(50));
        CacheMetrics m2 = new CacheMetrics();
        WriteThroughStrategy wt = new WriteThroughStrategy(new LRUCache(3, m2), new Database(m2));

        System.out.println("  Escrita sincrona em cache + banco:");
        wt.write("user:4", "Pedro Lima");
        System.out.println("  Leitura imediata (hit esperado):");
        wt.read("user:4");
        m2.print("Write-Through");

        // Write-Behind
        System.out.println("\n[3] WRITE-BEHIND");
        System.out.println("-".repeat(50));
        CacheMetrics m3 = new CacheMetrics();
        WriteBehindStrategy wb = new WriteBehindStrategy(new LRUCache(5, m3), new Database(m3));

        System.out.println("  Escritas rapidas (banco ainda nao atualizado):");
        wb.write("counter:views", "1000");
        wb.write("counter:clicks", "350");
        wb.write("counter:shares", "75");
        System.out.printf("  Pendentes na fila: %d%n", wb.pendingWrites());
        wb.flush();
        System.out.printf("  Pendentes apos flush: %d%n", wb.pendingWrites());
        m3.print("Write-Behind");

        // LRU Eviction
        System.out.println("\n[4] LRU EVICTION (capacidade = 3)");
        System.out.println("-".repeat(50));
        CacheMetrics m4 = new CacheMetrics();
        LRUCache lru = new LRUCache(3, m4);
        lru.put("a", "1"); lru.put("b", "2"); lru.put("c", "3");
        System.out.println("  Acessando 'a' para torna-lo recente:");
        lru.get("a");
        System.out.println("  Inserindo 'd' -> 'b' deve ser evicted:");
        lru.put("d", "4");

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  Trade-offs:");
        System.out.println("  Cache-Aside  -> leitura eficiente, consistencia eventual");
        System.out.println("  Write-Through -> consistencia forte, escrita mais lenta");
        System.out.println("  Write-Behind  -> escrita ultra-rapida, risco de perda");
        System.out.println("  Proximo: Ep.05 - SQL vs NoSQL");
        System.out.println("=".repeat(60));
    }
}
