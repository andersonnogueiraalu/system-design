package com.system.design.ep05;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Ep.05 — SQL vs NoSQL: quando usar cada um
 * Serie: System Design que TODO dev Java precisa dominar
 *
 * Demonstra na pratica os dois modelos de persistencia:
 *
 *   SQL (Relacional)
 *     - Dados estruturados em tabelas com schema rigido
 *     - Relacionamentos via chaves estrangeiras + JOIN
 *     - ACID: Atomicity, Consistency, Isolation, Durability
 *     - Consulta por ID:     O(log n) com indice B-Tree
 *     - JOIN entre tabelas:  O(n * m) sem indice, O(n log m) com indice
 *
 *   NoSQL (Documento — estilo MongoDB)
 *     - Dados como documentos JSON embutidos (sem JOIN)
 *     - Schema flexivel — cada documento pode ter campos diferentes
 *     - BASE: Basically Available, Soft state, Eventually consistent
 *     - Leitura por ID:     O(1) com hash index
 *     - Leitura full-scan:  O(n)
 *
 * Em producao:
 *   SQL     → Spring Data JPA + PostgreSQL/MySQL
 *   NoSQL   → Spring Data MongoDB / DynamoDB SDK
 *
 * @author Anderson Nogueira
 * @see <a href="https://linkedin.com/in/anderson-nogueira">LinkedIn</a>
 */
public class SqlVsNoSqlExemplo {
    // ─────────────────────────────────────────────────────────────────────────
    // METRICAS — rastreia operacoes e tempo de acesso simulado
    // ─────────────────────────────────────────────────────────────────────────

    static class QueryMetrics {
        private final AtomicInteger reads  = new AtomicInteger(0);
        private final AtomicInteger writes = new AtomicInteger(0);
        private final AtomicInteger joins  = new AtomicInteger(0);
        private long totalLatencyMs        = 0;

        public void read(long latencyMs)  { reads.incrementAndGet();  totalLatencyMs += latencyMs; }
        public void write(long latencyMs) { writes.incrementAndGet(); totalLatencyMs += latencyMs; }
        public void join()                { joins.incrementAndGet(); }

        public void print(String dbName) {
            int ops = reads.get() + writes.get();
            System.out.printf("%n  [Metricas - %s]%n", dbName);
            System.out.printf("  Reads: %d | Writes: %d | JOINs: %d%n",
                    reads.get(), writes.get(), joins.get());
            System.out.printf("  Latencia media simulada: %dms%n",
                    ops > 0 ? totalLatencyMs / ops : 0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MODELO SQL — tabelas, schema rigido, JOINs, ACID
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registro de usuario — simula uma linha da tabela USERS.
     * Schema rigido: todo usuario DEVE ter id, name e email.
     */
    static class UserRecord {
        final int    id;
        final String name;
        final String email;

        UserRecord(int id, String name, String email) {
            this.id = id; this.name = name; this.email = email;
        }

        @Override public String toString() {
            return String.format("UserRecord{id=%d, name='%s', email='%s'}", id, name, email);
        }
    }

    /**
     * Registro de pedido — simula uma linha da tabela ORDERS.
     * Chave estrangeira userId referencia USERS(id).
     */
    static class OrderRecord {
        final int    id;
        final int    userId;   // FK -> USERS(id)
        final String product;
        final double price;

        OrderRecord(int id, int userId, String product, double price) {
            this.id = id; this.userId = userId;
            this.product = product; this.price = price;
        }

        @Override public String toString() {
            return String.format("OrderRecord{id=%d, userId=%d, product='%s', price=%.2f}",
                    id, userId, product, price);
        }
    }

    /**
     * Banco relacional simulado com duas tabelas e suporte a JOIN.
     *
     * Estrutura interna:
     *   usersTable  → TreeMap<id, UserRecord>  — simula B-Tree index    O(log n)
     *   ordersTable → List<OrderRecord>        — sem indice por userId  O(n) no JOIN
     *
     * Em producao: PostgreSQL ou MySQL com indices compostos reduzem
     *              o JOIN para O(n log m).
     */
    static class RelationalDatabase {
        // TreeMap simula B-Tree index do banco relacional — busca O(log n)
        private final TreeMap<Integer, UserRecord>  usersTable  = new TreeMap<>();
        private final List<OrderRecord>             ordersTable = new ArrayList<>();
        private int nextOrderId = 1;
        private final QueryMetrics metrics;

        RelationalDatabase(QueryMetrics metrics) { this.metrics = metrics; }

        /**
         * INSERT INTO users (id, name, email) VALUES (...)
         * Complexidade: O(log n) — insercao em B-Tree
         */
        public void insertUser(int id, String name, String email) {
            usersTable.put(id, new UserRecord(id, name, email));
            metrics.write(5); // ~5ms simulado
            System.out.printf("    [SQL INSERT] users: id=%d, name='%s'%n", id, name);
        }

        /**
         * INSERT INTO orders (userId, product, price) VALUES (...)
         * Complexidade: O(1) amortizado
         */
        public void insertOrder(int userId, String product, double price) {
            ordersTable.add(new OrderRecord(nextOrderId++, userId, product, price));
            metrics.write(5);
            System.out.printf("    [SQL INSERT] orders: userId=%d, product='%s'%n", userId, product);
        }

        /**
         * SELECT * FROM users WHERE id = ?
         * Complexidade: O(log n) com B-Tree index
         *
         * @param id chave primaria do usuario
         */
        public Optional<UserRecord> findUserById(int id) {
            metrics.read(3); // ~3ms com indice
            UserRecord u = usersTable.get(id);
            if (u != null) System.out.printf("    [SQL SELECT] users WHERE id=%d -> encontrado%n", id);
            else           System.out.printf("    [SQL SELECT] users WHERE id=%d -> nao encontrado%n", id);
            return Optional.ofNullable(u);
        }

        /**
         * SELECT u.name, o.product, o.price
         *   FROM users u
         *   JOIN orders o ON o.userId = u.id
         *  WHERE u.id = ?
         *
         * Complexidade: O(log n) para buscar user + O(m) para varrer orders
         *   onde n = total de usuarios, m = total de pedidos
         * Com indice em orders.userId: O(log n + log m)
         *
         * @param userId chave primaria do usuario
         */
        public List<String> findOrdersByUser(int userId) {
            metrics.join();
            metrics.read(15); // JOIN custa mais — ~15ms sem indice em FK
            Optional<UserRecord> user = findUserById(userId);
            if (user.isEmpty()) return Collections.emptyList();

            List<String> result = ordersTable.stream()
                    .filter(o -> o.userId == userId)
                    .map(o -> String.format("  %s -> %s (R$ %.2f)", user.get().name, o.product, o.price))
                    .collect(Collectors.toList());

            System.out.printf("    [SQL JOIN]   %d pedido(s) encontrado(s) para userId=%d%n",
                    result.size(), userId);
            return result;
        }

        /**
         * BEGIN TRANSACTION
         *   UPDATE + INSERT atomicos
         * COMMIT / ROLLBACK
         *
         * Simula ACID: se qualquer operacao falhar, nenhuma e persistida.
         *
         * @param userId    usuario a atualizar
         * @param newEmail  novo email (UPDATE)
         * @param product   novo pedido (INSERT)
         * @param price     preco do pedido
         * @param simulateFail true para forcar rollback e demonstrar atomicidade
         */
        public void executeTransaction(int userId, String newEmail,
                                       String product, double price,
                                       boolean simulateFail) {
            System.out.println("    [SQL BEGIN TRANSACTION]");
            UserRecord original = usersTable.get(userId);
            if (original == null) { System.out.println("    [SQL ROLLBACK] usuario nao encontrado"); return; }

            // Snapshot para rollback
            UserRecord backup    = new UserRecord(original.id, original.name, original.email);
            int        backupIdx = nextOrderId;

            try {
                usersTable.put(userId, new UserRecord(userId, original.name, newEmail));
                System.out.printf("    [SQL UPDATE] users SET email='%s' WHERE id=%d%n", newEmail, userId);
                metrics.write(5);

                if (simulateFail) throw new RuntimeException("Falha simulada!");

                ordersTable.add(new OrderRecord(nextOrderId++, userId, product, price));
                System.out.printf("    [SQL INSERT] orders: product='%s'%n", product);
                metrics.write(5);

                System.out.println("    [SQL COMMIT] Transacao confirmada com sucesso");
            } catch (RuntimeException e) {
                // ROLLBACK — desfaz tudo
                usersTable.put(userId, backup);
                nextOrderId = backupIdx;
                System.out.printf("    [SQL ROLLBACK] %s — estado restaurado%n", e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MODELO NoSQL — documentos JSON, schema flexivel, sem JOIN
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Documento de usuario — simula um documento MongoDB.
     *
     * Diferenca fundamental do modelo relacional:
     *   - Pedidos sao EMBUTIDOS no documento (embedded documents)
     *   - Sem JOIN: todos os dados chegam em uma unica leitura
     *   - Schema flexivel: o campo "metadata" pode existir ou nao
     */
    static class UserDocument {
        final String              id;
        final String              name;
        final String              email;
        final List<Map<String, Object>> orders; // pedidos embutidos — sem JOIN!
        Map<String, Object>       metadata;     // campo opcional — schema flexivel

        UserDocument(String id, String name, String email) {
            this.id = id; this.name = name; this.email = email;
            this.orders = new ArrayList<>();
        }

        /** Adiciona pedido embutido — elimina a necessidade de JOIN. O(1) amortizado */
        public void addOrder(String product, double price) {
            Map<String, Object> order = new LinkedHashMap<>();
            order.put("product", product);
            order.put("price",   price);
            order.put("ts",      System.currentTimeMillis());
            orders.add(order);
        }

        /** Adiciona campo extra sem alterar schema dos outros documentos. */
        public void setMetadata(String key, Object value) {
            if (metadata == null) metadata = new LinkedHashMap<>();
            metadata.put(key, value);
        }

        @Override public String toString() {
            return String.format("UserDoc{id='%s', name='%s', orders=%d, meta=%s}",
                    id, name, orders.size(), metadata);
        }
    }

    /**
     * Banco de documentos simulado — estilo MongoDB.
     *
     * Estrutura interna:
     *   collection → HashMap<id, UserDocument>  — hash index  O(1) leitura/escrita
     *
     * Vantagem sobre SQL:
     *   - Leitura completa do usuario + pedidos em O(1) (sem JOIN)
     *   - Schema livre — novos campos sem ALTER TABLE
     *
     * Desvantagem:
     *   - Duplicacao de dados se o mesmo dado aparece em varios documentos
     *   - Sem garantia de consistencia entre documentos (eventual consistency)
     *   - Consultas por campos nao-indexados: O(n) full collection scan
     */
    static class DocumentDatabase {
        // HashMap simula o hash index do MongoDB — O(1) por _id
        private final Map<String, UserDocument> collection = new HashMap<>();
        private final QueryMetrics metrics;

        DocumentDatabase(QueryMetrics metrics) { this.metrics = metrics; }

        /**
         * db.users.insertOne({...})
         * Complexidade: O(1)
         */
        public void insertUser(String id, String name, String email) {
            collection.put(id, new UserDocument(id, name, email));
            metrics.write(2); // ~2ms — sem overhead de B-Tree
            System.out.printf("    [NoSQL INSERT] _id='%s', name='%s'%n", id, name);
        }

        /**
         * db.users.updateOne({_id}, {$push: {orders: {...}}})
         *
         * Pedido embutido no documento — elimina a necessidade de JOIN.
         * Complexidade: O(1) — find por hash + append na lista interna
         */
        public void addOrderToUser(String userId, String product, double price) {
            UserDocument doc = collection.get(userId);
            if (doc == null) { System.out.printf("    [NoSQL ERROR] _id='%s' nao encontrado%n", userId); return; }
            doc.addOrder(product, price);
            metrics.write(2);
            System.out.printf("    [NoSQL UPDATE] _id='%s' +order('%s')%n", userId, product);
        }

        /**
         * db.users.findOne({_id: ?})
         *
         * Retorna usuario + todos os pedidos em UMA UNICA operacao.
         * Sem JOIN. Sem segunda query.
         * Complexidade: O(1) com hash index
         */
        public Optional<UserDocument> findById(String id) {
            metrics.read(1); // ~1ms — O(1) hash lookup
            UserDocument doc = collection.get(id);
            if (doc != null) System.out.printf("    [NoSQL FIND]   _id='%s' -> encontrado (%d pedidos embutidos)%n",
                    id, doc.orders.size());
            else             System.out.printf("    [NoSQL FIND]   _id='%s' -> nao encontrado%n", id);
            return Optional.ofNullable(doc);
        }

        /**
         * db.users.find({}) — full collection scan
         *
         * Complexidade: O(n) — varre todos os documentos
         * Em producao: criar indice no campo buscado para reduzir a O(log n)
         */
        public List<UserDocument> findByName(String name) {
            metrics.read(20); // ~20ms — full scan sem indice
            System.out.printf("    [NoSQL SCAN]   buscando name='%s' (full collection scan)%n", name);
            return collection.values().stream()
                    .filter(d -> d.name.equalsIgnoreCase(name))
                    .collect(Collectors.toList());
        }

        /** Adiciona campo extra a documento sem afetar os demais — schema livre. */
        public void addMetadata(String userId, String key, Object value) {
            UserDocument doc = collection.get(userId);
            if (doc != null) {
                doc.setMetadata(key, value);
                System.out.printf("    [NoSQL UPDATE] _id='%s' +meta(%s=%s)%n", userId, key, value);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEMONSTRACAO
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("  Ep.05 - SQL vs NoSQL: quando usar cada um");
        System.out.println("  System Design que TODO dev Java precisa dominar");
        System.out.println("=".repeat(60));

        // ── SQL ──
        System.out.println("\n[SQL] Banco Relacional — schema rigido, JOINs, ACID");
        System.out.println("-".repeat(55));

        QueryMetrics sqlMetrics = new QueryMetrics();
        RelationalDatabase sql  = new RelationalDatabase(sqlMetrics);

        sql.insertUser(1, "Anderson Nogueira", "anderson@email.com");
        sql.insertUser(2, "Maria Silva",       "maria@email.com");
        sql.insertOrder(1, "Curso Java",   199.90);
        sql.insertOrder(1, "Livro DDD",    89.90);
        sql.insertOrder(2, "Curso Kotlin", 149.90);

        System.out.println("\n  Busca por ID (O(log n) — B-Tree index):");
        sql.findUserById(1).ifPresent(u -> System.out.printf("    -> %s%n", u));

        System.out.println("\n  JOIN usuarios + pedidos:");
        sql.findOrdersByUser(1).forEach(System.out::println);

        System.out.println("\n  Transacao ACID — simulando ROLLBACK:");
        sql.executeTransaction(1, "novo@email.com", "Curso Spring", 299.90, true);

        System.out.println("\n  Transacao ACID — COMMIT bem-sucedido:");
        sql.executeTransaction(1, "novo@email.com", "Curso Spring", 299.90, false);

        sqlMetrics.print("SQL (PostgreSQL-like)");

        // ── NoSQL ──
        System.out.println("\n[NoSQL] Banco de Documentos — schema livre, sem JOIN");
        System.out.println("-".repeat(55));

        QueryMetrics noSqlMetrics = new QueryMetrics();
        DocumentDatabase nosql    = new DocumentDatabase(noSqlMetrics);

        nosql.insertUser("u1", "Anderson Nogueira", "anderson@email.com");
        nosql.insertUser("u2", "Maria Silva",       "maria@email.com");
        nosql.addOrderToUser("u1", "Curso Java",   199.90);
        nosql.addOrderToUser("u1", "Livro DDD",    89.90);
        nosql.addOrderToUser("u2", "Curso Kotlin", 149.90);

        System.out.println("\n  Leitura completa sem JOIN (O(1) — hash index):");
        nosql.findById("u1").ifPresent(doc -> {
            System.out.printf("    -> %s%n", doc);
            System.out.println("    Pedidos embutidos:");
            doc.orders.forEach(o -> System.out.printf("      - %s: R$ %.2f%n",
                    o.get("product"), o.get("price")));
        });

        System.out.println("\n  Schema livre — campo extra sem ALTER TABLE:");
        nosql.addMetadata("u1", "plano", "premium");
        nosql.addMetadata("u1", "origem", "linkedin");
        nosql.findById("u1").ifPresent(doc ->
                System.out.printf("    -> metadata: %s%n", doc.metadata));

        System.out.println("\n  Full collection scan sem indice (O(n)):");
        nosql.findByName("Maria Silva").forEach(d -> System.out.printf("    -> %s%n", d));

        noSqlMetrics.print("NoSQL (MongoDB-like)");

        // ── COMPARATIVO ──
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  Resumo do trade-off:");
        System.out.println("  SQL    -> consistencia, relacionamentos, transacoes ACID");
        System.out.println("  NoSQL  -> escala horizontal, schema livre, leitura rapida");
        System.out.println();
        System.out.println("  Quando usar SQL:");
        System.out.println("  - Financeiro, e-commerce, ERP (consistencia e-prioritaria)");
        System.out.println("  - Dados com relacionamentos complexos e previstos");
        System.out.println();
        System.out.println("  Quando usar NoSQL:");
        System.out.println("  - Catalogo de produtos, perfis de usuario, feeds sociais");
        System.out.println("  - Alto volume de escrita, schema evolui com frequencia");
        System.out.println("  - Dados hierarquicos que chegam juntos e sao lidos juntos");
        System.out.println();
        System.out.println("  Proximo: Ep.06 - Message Queue: Kafka e RabbitMQ");
        System.out.println("=".repeat(60));
    }
}
