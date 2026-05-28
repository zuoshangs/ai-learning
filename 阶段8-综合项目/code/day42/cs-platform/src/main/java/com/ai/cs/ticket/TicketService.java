package com.ai.cs.ticket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Ticket service: create, assign, process, close work orders.
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public TicketService() {
        seedTickets();
    }

    /** Create a new ticket */
    public Ticket createTicket(String title, String description, String category,
                                Ticket.Priority priority, String creatorName) {
        Ticket ticket = new Ticket(title, description, category, priority, creatorName);
        tickets.put(ticket.getId(), ticket);
        log.info("Ticket created: {} ({})", ticket.getId().substring(0, 8), title);
        return ticket;
    }

    /** Assign ticket to a person */
    public Ticket assignTicket(String id, String assignee) {
        Ticket ticket = getTicketOrThrow(id);
        ticket.setAssignee(assignee);
        ticket.setUpdatedAt(Instant.now().toEpochMilli());
        if (ticket.getStatus() == Ticket.Status.PENDING) {
            ticket.setStatus(Ticket.Status.IN_PROGRESS);
        }
        return ticket;
    }

    /** Update ticket status with validation */
    public Ticket updateStatus(String id, Ticket.Status newStatus, String resolution) {
        Ticket ticket = getTicketOrThrow(id);
        Ticket.Status current = ticket.getStatus();

        // Validate status transitions
        if (!isValidTransition(current, newStatus)) {
            throw new IllegalArgumentException(
                    "Cannot transition from " + current + " to " + newStatus);
        }

        ticket.setStatus(newStatus);
        ticket.setUpdatedAt(Instant.now().toEpochMilli());

        if (newStatus == Ticket.Status.RESOLVED) {
            ticket.setResolvedAt(Instant.now().toEpochMilli());
            if (resolution != null && !resolution.isBlank()) {
                ticket.setResolution(resolution);
            }
        }

        return ticket;
    }

    /** Get a single ticket */
    public Ticket getTicket(String id) {
        return tickets.get(id);
    }

    /** List all tickets with optional filters */
    public List<Ticket> listTickets(Ticket.Status status, Ticket.Priority priority,
                                     String category, String assignee) {
        return tickets.values().stream()
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> priority == null || t.getPriority() == priority)
                .filter(t -> category == null || category.isBlank()
                        || category.equals(t.getCategory()))
                .filter(t -> assignee == null || assignee.isBlank()
                        || assignee.equals(t.getAssignee()))
                .sorted(Comparator.comparingLong(Ticket::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /** Delete a ticket */
    public boolean deleteTicket(String id) {
        tickets.remove(id);
        return true;
    }

    /** Get statistics */
    public TicketStats getStats() {
        TicketStats stats = new TicketStats();
        stats.total = tickets.size();

        for (Ticket t : tickets.values()) {
            // Count by status
            switch (t.getStatus()) {
                case PENDING -> stats.pending++;
                case IN_PROGRESS -> stats.inProgress++;
                case RESOLVED -> stats.resolved++;
                case CLOSED -> stats.closed++;
            }
            // Count by priority
            stats.byPriority.merge(t.getPriority().displayName, 1, Integer::sum);
            // Count by category
            stats.byCategory.merge(t.getCategory(), 1, Integer::sum);
            // Track resolution time
            if (t.getResolvedAt() != null) {
                long hours = (t.getResolvedAt() - t.getCreatedAt()) / 3600000;
                stats.totalResolutionHours += hours;
                stats.resolvedCount++;
            }
        }

        if (stats.resolvedCount > 0) {
            stats.avgResolutionHours = (double) stats.totalResolutionHours / stats.resolvedCount;
        }

        return stats;
    }

    /** Get all unique categories used in tickets */
    public Set<String> getCategories() {
        return tickets.values().stream()
                .map(Ticket::getCategory)
                .collect(Collectors.toSet());
    }

    // ---- Internal ----

    private Ticket getTicketOrThrow(String id) {
        Ticket ticket = tickets.get(id);
        if (ticket == null) throw new NoSuchElementException("Ticket not found: " + id);
        return ticket;
    }

    private boolean isValidTransition(Ticket.Status from, Ticket.Status to) {
        return switch (from) {
            case PENDING -> to == Ticket.Status.IN_PROGRESS || to == Ticket.Status.CLOSED;
            case IN_PROGRESS -> to == Ticket.Status.RESOLVED || to == Ticket.Status.PENDING;
            case RESOLVED -> to == Ticket.Status.CLOSED || to == Ticket.Status.IN_PROGRESS;
            case CLOSED -> false; // Cannot change closed tickets
        };
    }

    private void seedTickets() {
        Ticket t1 = createTicket("无法登录账号", "用户反馈无法登录账号，提示密码错误。已尝试重置密码但仍无法登录。",
                "账号", Ticket.Priority.HIGH, "张三");
        t1.setStatus(Ticket.Status.IN_PROGRESS);
        t1.setAssignee("客服A");
        t1.setCreatorContact("zhangsan@example.com");

        Ticket t2 = createTicket("退货退款申请", "购买的商品与描述不符，要求退货退款。订单号：ORD-2026-0520。",
                "售后", Ticket.Priority.URGENT, "李四");
        t2.setStatus(Ticket.Status.RESOLVED);
        t2.setAssignee("客服B");
        t2.setResolvedAt(t2.getCreatedAt() + 3600000 * 3); // 3 hours
        t2.setResolution("已同意退货申请，退款将在收到退货后3个工作日内处理。");

        Ticket t3 = createTicket("物流延迟查询", "快递已3天未更新物流信息，订单号：ORD-2026-0518。",
                "物流", Ticket.Priority.MEDIUM, "王五");
        t3.setCreatorContact("wangwu@example.com");

        Ticket t4 = createTicket("会员积分问题", "消费后积分未到账，消费金额299元。",
                "会员", Ticket.Priority.LOW, "赵六");

        log.info("Seeded {} tickets", tickets.size());
    }

    // ---- Stats DTO ----
    public static class TicketStats {
        public int total = 0;
        public int pending = 0;
        public int inProgress = 0;
        public int resolved = 0;
        public int closed = 0;
        public int resolvedCount = 0;
        public long totalResolutionHours = 0;
        public double avgResolutionHours = 0;
        public Map<String, Integer> byPriority = new LinkedHashMap<>();
        public Map<String, Integer> byCategory = new LinkedHashMap<>();

        public String toJson() {
            return String.format("""
                    {"total":%d,"pending":%d,"inProgress":%d,"resolved":%d,"closed":%d,
                    "avgResolutionHours":%.1f,"byPriority":%s,"byCategory":%s}
                    """, total, pending, inProgress, resolved, closed, avgResolutionHours,
                    mapToJson(byPriority), mapToJson(byCategory));
        }

        private String mapToJson(Map<String, Integer> m) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var e : m.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
    }
}
