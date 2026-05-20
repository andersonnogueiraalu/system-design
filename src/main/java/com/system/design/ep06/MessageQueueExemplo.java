package com.system.design.ep06;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ep.06 — Message Queue: Kafka e RabbitMQ na pratica
 *
 * <p>Simula os principais conceitos de Message Queue aplicados em sistemas Java:
 * <ul>
 *   <li>Producer / Consumer desacoplados</li>
 *   <li>Dead Letter Queue (DLQ)</li>
 *   <li>Idempotency Key — evita processamento duplicado</li>
 *   <li>Consumer Group — multiplos consumidores dividem carga</li>
 *   <li>At-least-once vs Exactly-once delivery</li>
 * </ul>
 *
 * <p>Complexidade:
 * <ul>
 *   <li>publish()        — O(1)</li>
 *   <li>consume()        — O(1) por mensagem</li>
 *   <li>isDuplicate()    — O(1) com HashSet/HashMap</li>
 *   <li>routeToGroup()   — O(k) onde k = numero de consumidores no grupo</li>
 * </ul>
 *
 * <p>Serie: System Design que TODO dev Java precisa dominar
 * Autor: Anderson Nogueira | linkedin.com/in/anderson-freitas21
 */
public class MessageQueueExemplo {
    // ─────────────────────────────────────────────
    // MODEL
    // ─────────────────────────────────────────────

    /**
     * Representa uma mensagem publicada no topico.
     * Carrega um idempotencyKey para garantir exactly-once processing.
     *
     * <p>Big O: criacao O(1), comparacao por key O(1) via HashMap.
     */
    static class Message {
        final String id;           // idempotency key
        final String topic;
        final String payload;
        int retryCount;

        Message(String id, String topic, String payload) {
            this.id = id;
            this.topic = topic;
            this.payload = payload;
            this.retryCount = 0;
        }

        @Override
        public String toString() {
            return "[" + topic + "] id=" + id + " | payload=" + payload
                    + " | retries=" + retryCount;
        }
    }

    // ─────────────────────────────────────────────
    // MESSAGE BROKER — simula topico Kafka / exchange RabbitMQ
    // ─────────────────────────────────────────────

    /**
     * Broker simples com suporte a topico principal e Dead Letter Queue.
     *
     * <p>Big O:
     * <ul>
     *   <li>publish()  — O(1) amortizado (LinkedList offer)</li>
     *   <li>poll()     — O(1)</li>
     *   <li>size()     — O(1)</li>
     * </ul>
     */
    static class MessageBroker {
        private final String topicName;
        private final Queue<Message> mainQueue    = new LinkedList<>();
        private final Queue<Message> dlq          = new LinkedList<>();
        private final int maxRetries;

        MessageBroker(String topicName, int maxRetries) {
            this.topicName  = topicName;
            this.maxRetries = maxRetries;
        }

        /** Publica mensagem no topico principal — O(1). */
        void publish(Message msg) {
            mainQueue.offer(msg);
            System.out.println("  [BROKER] Publicado: " + msg);
        }

        /** Consome proxima mensagem do topico — O(1). */
        Message poll() {
            return mainQueue.poll();
        }

        /** Envia mensagem para a Dead Letter Queue apos esgotar retries — O(1). */
        void sendToDLQ(Message msg) {
            dlq.offer(msg);
            System.out.println("  [DLQ] Mensagem encaminhada para Dead Letter Queue: " + msg.id);
        }

        /** Verifica se ainda ha mensagens no topico principal. */
        boolean hasMessages() {
            return !mainQueue.isEmpty();
        }

        int mainQueueSize() { return mainQueue.size(); }
        int dlqSize()       { return dlq.size(); }
        String getTopicName() { return topicName; }
        int getMaxRetries()   { return maxRetries; }

        /** Imprime conteudo da DLQ para inspecao. */
        void inspectDLQ() {
            System.out.println("\n  [DLQ INSPECT] " + dlq.size() + " mensagem(ns) na DLQ do topico '" + topicName + "':");
            for (Message m : dlq) {
                System.out.println("    " + m);
            }
        }
    }

    // ─────────────────────────────────────────────
    // IDEMPOTENT CONSUMER — exactly-once processing
    // ─────────────────────────────────────────────

    /**
     * Consumidor com controle de idempotencia via HashMap de IDs processados.
     *
     * <p>Garante que a mesma mensagem nao seja processada duas vezes,
     * mesmo que o broker entregue mais de uma vez (at-least-once).
     *
     * <p>Big O:
     * <ul>
     *   <li>isDuplicate()  — O(1) lookup em HashMap</li>
     *   <li>process()      — O(1) por mensagem</li>
     * </ul>
     */
    static class IdempotentConsumer {
        private final String name;
        private final Map<String, Boolean> processedIds = new HashMap<>();
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger duplicateCount = new AtomicInteger(0);

        IdempotentConsumer(String name) {
            this.name = name;
        }

        /**
         * Processa mensagem apenas se ainda nao foi processada.
         * Retorna true se processou com sucesso, false se duplicata.
         * Complexidade: O(1).
         */
        boolean process(Message msg) {
            if (processedIds.containsKey(msg.id)) {
                duplicateCount.incrementAndGet();
                System.out.println("  [" + name + "] DUPLICATA ignorada: " + msg.id);
                return false;
            }
            processedIds.put(msg.id, true);
            successCount.incrementAndGet();
            System.out.println("  [" + name + "] Processado: " + msg);
            return true;
        }

        void printStats() {
            System.out.println("  [" + name + "] Stats → processados=" + successCount.get()
                    + " | duplicatas ignoradas=" + duplicateCount.get());
        }
    }

    // ─────────────────────────────────────────────
    // CONSUMER GROUP — distribui carga entre instancias
    // ─────────────────────────────────────────────

    /**
     * Simula Consumer Group do Kafka: mensagens sao distribuidas
     * entre N consumidores usando round-robin.
     *
     * <p>Em Kafka real, cada particao e atribuida a exatamente um consumidor do grupo.
     * Aqui simulamos a distribuicao logica com round-robin.
     *
     * <p>Big O:
     * <ul>
     *   <li>dispatch()  — O(1) por mensagem (mod + lookup por indice)</li>
     * </ul>
     */
    static class ConsumerGroup {
        private final String groupId;
        private final List<IdempotentConsumer> consumers = new ArrayList<>();
        private int roundRobinIndex = 0;

        ConsumerGroup(String groupId) {
            this.groupId = groupId;
        }

        void addConsumer(IdempotentConsumer consumer) {
            consumers.add(consumer);
        }

        /**
         * Distribui mensagem para o proximo consumidor via round-robin — O(1).
         */
        boolean dispatch(Message msg) {
            if (consumers.isEmpty()) return false;
            IdempotentConsumer target = consumers.get(roundRobinIndex % consumers.size());
            roundRobinIndex++;
            return target.process(msg);
        }

        void printGroupStats() {
            System.out.println("\n  [GROUP:" + groupId + "] Stats por consumidor:");
            consumers.forEach(IdempotentConsumer::printStats);
        }
    }

    // ─────────────────────────────────────────────
    // RETRY PROCESSOR — simula at-least-once com DLQ
    // ─────────────────────────────────────────────

    /**
     * Processa mensagem com logica de retry e Dead Letter Queue.
     * Simula falha em mensagens especificas para demonstrar o fluxo.
     *
     * <p>Big O: O(maxRetries) por mensagem no pior caso.
     */
    static class RetryProcessor {

        /**
         * Tenta processar a mensagem ate maxRetries vezes.
         * Se esgotar, envia para a DLQ.
         */
        static void processWithRetry(Message msg, MessageBroker broker, boolean simulateFailure) {
            if (!simulateFailure) {
                System.out.println("  [RETRY] Sucesso apos 0 tentativa(s): " + msg.id);
                return;
            }
            while (msg.retryCount < broker.getMaxRetries()) {
                msg.retryCount++;
                System.out.println("  [RETRY] Falha tentativa " + msg.retryCount
                        + "/" + broker.getMaxRetries() + " para: " + msg.id);
            }
            // Esgotou todas as tentativas — envia para DLQ
            broker.sendToDLQ(msg);
        }
    }

    // ─────────────────────────────────────────────
    // DEMO
    // ─────────────────────────────────────────────

    public static void main(String[] args) {

        System.out.println("=".repeat(60));
        System.out.println(" Ep.06 — Message Queue: Kafka e RabbitMQ na pratica");
        System.out.println("=".repeat(60));

        // ── Demo 1: Producer / Consumer desacoplados ──────────────
        System.out.println("\n--- DEMO 1: Producer / Consumer basico ---");
        MessageBroker pedidosBroker = new MessageBroker("pedidos", 3);

        // Producer publica 3 pedidos
        pedidosBroker.publish(new Message("order-001", "pedidos", "Pizza Margherita x2"));
        pedidosBroker.publish(new Message("order-002", "pedidos", "Hamburguer Duplo x1"));
        pedidosBroker.publish(new Message("order-003", "pedidos", "Suco de Laranja x3"));

        System.out.println("\n  Broker recebeu " + pedidosBroker.mainQueueSize() + " mensagens.\n");

        // Consumer processa no proprio ritmo
        IdempotentConsumer cozinha = new IdempotentConsumer("Cozinha-1");
        while (pedidosBroker.hasMessages()) {
            Message msg = pedidosBroker.poll();
            cozinha.process(msg);
        }

        // ── Demo 2: Idempotency Key — exactly-once ────────────────
        System.out.println("\n--- DEMO 2: Idempotency Key (exactly-once) ---");
        MessageBroker pagamentoBroker = new MessageBroker("pagamentos", 3);

        Message pag1 = new Message("pag-100", "pagamentos", "R$150.00 - cartao");
        Message pag1Duplicata = new Message("pag-100", "pagamentos", "R$150.00 - cartao DUPLICATA");
        Message pag2 = new Message("pag-101", "pagamentos", "R$89.90 - pix");

        pagamentoBroker.publish(pag1);
        pagamentoBroker.publish(pag1Duplicata);  // broker entregou duas vezes (at-least-once)
        pagamentoBroker.publish(pag2);

        System.out.println();
        IdempotentConsumer financeiroConsumer = new IdempotentConsumer("Financeiro-1");
        while (pagamentoBroker.hasMessages()) {
            financeiroConsumer.process(pagamentoBroker.poll());
        }
        financeiroConsumer.printStats();

        // ── Demo 3: Consumer Group — distribuicao de carga ───────
        System.out.println("\n--- DEMO 3: Consumer Group (round-robin) ---");
        MessageBroker notificacaoBroker = new MessageBroker("notificacoes", 3);
        for (int i = 1; i <= 6; i++) {
            notificacaoBroker.publish(new Message("notif-" + i, "notificacoes", "Evento " + i));
        }

        ConsumerGroup notifGroup = new ConsumerGroup("notif-consumers");
        notifGroup.addConsumer(new IdempotentConsumer("Notif-Worker-A"));
        notifGroup.addConsumer(new IdempotentConsumer("Notif-Worker-B"));
        notifGroup.addConsumer(new IdempotentConsumer("Notif-Worker-C"));

        System.out.println();
        while (notificacaoBroker.hasMessages()) {
            notifGroup.dispatch(notificacaoBroker.poll());
        }
        notifGroup.printGroupStats();

        // ── Demo 4: Dead Letter Queue — retry + DLQ ──────────────
        System.out.println("\n--- DEMO 4: Dead Letter Queue (retry + DLQ) ---");
        MessageBroker emailBroker = new MessageBroker("emails", 3);

        emailBroker.publish(new Message("email-001", "emails", "Confirmacao de cadastro"));
        emailBroker.publish(new Message("email-002", "emails", "Mensagem com servidor indisponivel"));
        emailBroker.publish(new Message("email-003", "emails", "Alerta de seguranca"));

        System.out.println();
        // email-002 simula falha persistente
        Message m;
        boolean[] simulaFalha = {false, true, false};
        int i = 0;
        while (emailBroker.hasMessages()) {
            m = emailBroker.poll();
            RetryProcessor.processWithRetry(m, emailBroker, simulaFalha[i++]);
        }

        emailBroker.inspectDLQ();

        System.out.println("\n" + "=".repeat(60));
        System.out.println(" Resumo dos conceitos demonstrados:");
        System.out.println("  1. Producer/Consumer desacoplados — O(1) publish/consume");
        System.out.println("  2. Idempotency Key — O(1) dedup via HashMap");
        System.out.println("  3. Consumer Group round-robin — O(1) dispatch");
        System.out.println("  4. Retry + Dead Letter Queue — O(maxRetries) worst case");
        System.out.println("=".repeat(60));
        System.out.println(" Serie: System Design que TODO dev Java precisa dominar");
        System.out.println(" Ep.06 — Message Queue: Kafka e RabbitMQ na pratica");
        System.out.println("=".repeat(60));
    }
}
