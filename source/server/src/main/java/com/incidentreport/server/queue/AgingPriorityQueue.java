package com.incidentreport.server.queue;

import com.incidentreport.common.model.IncidentTicket;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Hàng đợi sự cố ưu tiên kết hợp cơ chế Aging (chống đói - Starvation-free):
 * PriorityScore = (BaseSeverity * 100) + (WaitingSeconds * AgingFactor) + (ReportCount * 20)
 */
public class AgingPriorityQueue {

    public static class QueueItem {
        private final IncidentTicket ticket;
        private final long queuedTimeMs;
        private double dynamicScore;

        public QueueItem(IncidentTicket ticket) {
            this.ticket = ticket;
            this.queuedTimeMs = System.currentTimeMillis();
            recalculateScore();
        }

        public void recalculateScore() {
            long waitingSeconds = (System.currentTimeMillis() - queuedTimeMs) / 1000;
            // Aging: Mỗi 10 giây chờ được cộng thêm 5 điểm
            double agingBonus = waitingSeconds * 0.5;
            // Gom nhóm: Mỗi người báo thêm được cộng 20 điểm
            double reportBonus = (ticket.getReportCount() - 1) * 20.0;
            // Điểm cơ bản theo mức độ (1-5) * 100
            this.dynamicScore = (ticket.getBaseSeverity() * 100.0) + agingBonus + reportBonus;
            this.ticket.setComputedPriority(this.dynamicScore);
        }

        public IncidentTicket getTicket() { return ticket; }
        public double getDynamicScore() { return dynamicScore; }
    }

    private final PriorityBlockingQueue<QueueItem> queue;

    public AgingPriorityQueue() {
        // Sắp xếp giảm dần theo dynamicScore (điểm cao nhất lên đầu)
        this.queue = new PriorityBlockingQueue<>(50, Comparator.comparingDouble(QueueItem::getDynamicScore).reversed());
    }

    public synchronized boolean containsTicketId(int ticketId) {
        for (QueueItem item : queue) {
            if (item.getTicket() != null && item.getTicket().getTicketId() == ticketId) {
                return true;
            }
        }
        return false;
    }

    public synchronized void enqueue(IncidentTicket ticket) {
        if (ticket != null && !containsTicketId(ticket.getTicketId())) {
            queue.offer(new QueueItem(ticket));
            System.out.printf("📥 [QUEUE] Đã đưa vào hàng đợi: %s | Severity=%d | Điểm ban đầu=%.1f\n",
                ticket.getTicketCode(), ticket.getBaseSeverity(), ticket.getComputedPriority());
        }
    }

    public IncidentTicket dequeue() throws InterruptedException {
        QueueItem item = queue.take();
        return item.getTicket();
    }

    public IncidentTicket poll() {
        QueueItem item = queue.poll();
        return item != null ? item.getTicket() : null;
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Định kỳ làm mới điểm số của toàn bộ hàng đợi để hiện tượng Aging có hiệu lực
     */
    public synchronized void updateAllScores() {
        if (queue.isEmpty()) return;
        QueueItem[] items = queue.toArray(new QueueItem[0]);
        queue.clear();
        for (QueueItem item : items) {
            item.recalculateScore();
            queue.offer(item);
        }
    }
}

