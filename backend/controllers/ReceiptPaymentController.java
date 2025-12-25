package controllers;

import services.ReceiptService;
import services.BalanceService;
import services.TransactionService;
import models.User;
import models.Transaction;
import database.ReceiptDAO;
import database.UserDAO;
import utils.AuthMiddleware;
import utils.ErrorResponse;
import utils.Logger;
import utils.RequestUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;

/**
 * Controller for handling receipt payment processing.
 * Provides endpoint for processing payments for receipts.
 */
public class ReceiptPaymentController {

    private static final ReceiptService receiptService = ReceiptService.getInstance();
    private static final String CONTEXT = "ReceiptPaymentController";

    /**
     * Handler for paying for a receipt.
     * POST /api/receipts/pay?receiptId=X&userId=Y
     */
    public static class PayReceiptHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            ErrorResponse.addCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                ErrorResponse.sendError(exchange, 405, "Method not allowed");
                return;
            }
            
            // Verify authentication
            String authenticatedUserId = AuthMiddleware.verifyAuth(exchange);
            if (authenticatedUserId == null) {
                AuthMiddleware.sendUnauthorized(exchange);
                return;
            }
            
            Map<String, String> query = RequestUtils.parseQuery(exchange.getRequestURI());
            try {
                String userIdStr = query.getOrDefault("userId", "");
                int receiptId = Integer.parseInt(query.getOrDefault("receiptId", "0"));
                
                if (userIdStr.isEmpty() || receiptId == 0) {
                    ErrorResponse.sendError(exchange, 400, "receiptId and userId are required");
                    return;
                }
                
                // Verify authenticated user matches
                if (!userIdStr.equals(authenticatedUserId)) {
                    ErrorResponse.sendError(exchange, 403, "Forbidden: Cannot pay for receipts as another user");
                    return;
                }
                
                ReceiptDAO receiptDAO = receiptService.getReceiptDAO();
                
                // Calculate amount owed
                float owedAmount = receiptDAO.calculateUserOwedAmount(receiptId, userIdStr);
                float paidAmount = receiptDAO.getPaidAmount(receiptId, userIdStr);
                float remainingAmount = owedAmount - paidAmount;
                
                if (remainingAmount <= 0) {
                    ErrorResponse.sendError(exchange, 400, "You have already paid your full amount");
                    return;
                }
                
                // Check user balance
                BalanceService balanceService = new BalanceService();
                double currentBalance = balanceService.getCurrentBalance(userIdStr);
                
                if (currentBalance < remainingAmount) {
                    ErrorResponse.sendError(exchange, 400, 
                        String.format("Insufficient balance. You have $%.2f, need $%.2f", currentBalance, remainingAmount));
                    return;
                }
                
                // Get receipt uploader
                String uploaderId = receiptDAO.getReceiptUploadedBy(receiptId);
                if (uploaderId == null) {
                    ErrorResponse.sendError(exchange, 400, "Receipt not found");
                    return;
                }
                
                // Deduct from payer's balance
                boolean balanceDeducted = balanceService.subtractFromBalance(
                    userIdStr,
                    remainingAmount,
                    BalanceService.TYPE_PAYMENT_SENT,
                    "Payment for receipt #" + receiptId,
                    String.valueOf(receiptId),
                    "receipt"
                );
                
                if (!balanceDeducted) {
                    ErrorResponse.sendError(exchange, 500, "Failed to deduct balance");
                    return;
                }
                
                // Add to uploader's balance
                boolean balanceAdded = balanceService.addToBalance(
                    uploaderId,
                    remainingAmount,
                    BalanceService.TYPE_PAYMENT_RECEIVED,
                    "Payment received for receipt #" + receiptId,
                    String.valueOf(receiptId),
                    "receipt"
                );
                
                if (!balanceAdded) {
                    // Rollback: add back to payer (auto-refund)
                    try {
                        boolean refunded = balanceService.addToBalance(
                            userIdStr,
                            remainingAmount,
                            BalanceService.TYPE_REFUND,
                            "Refund due to payment processing error",
                            String.valueOf(receiptId),
                            "receipt"
                        );
                        if (!refunded) {
                            Logger.error(CONTEXT, "CRITICAL: Failed to refund after payment processing error. User: " + userIdStr + ", Amount: " + remainingAmount);
                        }
                    } catch (Exception refundError) {
                        Logger.error(CONTEXT, "CRITICAL: Exception during refund: " + refundError.getMessage(), refundError);
                    }
                    ErrorResponse.sendError(exchange, 500, "Failed to process payment. Your balance has been refunded.");
                    return;
                }
                
                // Create transaction record
                TransactionService transactionService = new TransactionService();
                Transaction transaction = null;
                try {
                    transaction = transactionService.createTransaction(
                        userIdStr,
                        uploaderId,
                        remainingAmount,
                        TransactionService.TYPE_RECEIPT_PAYMENT,
                        "Payment for receipt #" + receiptId,
                        TransactionService.STATUS_COMPLETED,
                        String.valueOf(receiptId)
                    );
                } catch (Exception txError) {
                    Logger.warn(CONTEXT, "Warning: Failed to create transaction record: " + txError.getMessage());
                    // Don't fail payment if transaction record fails - balances are already updated
                }
                
                // CRITICAL FIX: Record payment and mark items in a single atomic transaction
                int itemsMarkedPaid = receiptDAO.recordPaymentAndMarkItems(receiptId, userIdStr, remainingAmount);
                
                if (itemsMarkedPaid < 0) {
                    // Payment recording failed - rollback balance operations
                    Logger.error(CONTEXT, "CRITICAL: Payment recording failed, rolling back balance operations");
                    try {
                        // Refund payer
                        balanceService.addToBalance(
                            userIdStr,
                            remainingAmount,
                            BalanceService.TYPE_REFUND,
                            "Refund due to payment recording error",
                            String.valueOf(receiptId),
                            "receipt"
                        );
                        // Deduct from uploader
                        balanceService.subtractFromBalance(
                            uploaderId,
                            remainingAmount,
                            BalanceService.TYPE_REFUND,
                            "Refund due to payment recording error",
                            String.valueOf(receiptId),
                            "receipt"
                        );
                        Logger.error(CONTEXT, "Rolled back payment due to recording failure");
                    } catch (Exception rollbackError) {
                        Logger.error(CONTEXT, "CRITICAL: Failed to rollback after payment recording error: " + rollbackError.getMessage(), rollbackError);
                    }
                    ErrorResponse.sendError(exchange, 500, "Failed to record payment. Your balance has been refunded.");
                    return;
                }
                
                Logger.info(CONTEXT, "Successfully recorded payment and marked " + itemsMarkedPaid + " items as paid");
                
                // OPTIMIZATION: Get sender's updated balance immediately
                double senderBalance = balanceService.getCurrentBalance(uploaderId);
                
                // OPTIMIZATION: Check if receipt should be marked as completed ASYNCHRONOUSLY
                final int finalReceiptId = receiptId;
                new Thread(() -> {
                    try {
                        receiptDAO.checkAndMarkReceiptCompleted(finalReceiptId);
                        Logger.debug(CONTEXT, "Background: Checked receipt completion status for receipt " + finalReceiptId);
                    } catch (Exception e) {
                        Logger.error(CONTEXT, "Error in background receipt completion check: " + e.getMessage(), e);
                    }
                }).start();
                
                // Optimized: Calculate new amounts
                float newPaidAmount = paidAmount + remainingAmount;
                float newOwedAmount = owedAmount - remainingAmount;
                float newOwedAmountExcludingPaid = receiptDAO.calculateUserOwedAmountExcludingPaid(receiptId, userIdStr);
                
                // Get user's name for payment info
                UserDAO userDAO = new UserDAO();
                User payer = userDAO.findUserById(userIdStr);
                String payerName = payer != null ? payer.getName() : "You";
                
                // Get item payment info from receipt_items table
                Map<Integer, Map<String, Object>> itemPaymentMap = receiptDAO.getItemPaymentInfoForReceipt(receiptId);
                
                // Build item payment info JSON with payer names
                JSONObject itemPaymentInfo = new JSONObject();
                for (Map.Entry<Integer, Map<String, Object>> entry : itemPaymentMap.entrySet()) {
                    int itemId = entry.getKey();
                    Map<String, Object> paymentData = entry.getValue();
                    String paidByUserId = (String) paymentData.get("paidBy");
                    
                    // Get payer's name
                    User itemPayer = userDAO.findUserById(paidByUserId);
                    String itemPayerName = itemPayer != null ? itemPayer.getName() : "Unknown";
                    
                    JSONObject paymentJson = new JSONObject()
                        .put("paidBy", paidByUserId)
                        .put("payerName", itemPayerName)
                        .put("paidAt", paymentData.get("paidAt"));
                    itemPaymentInfo.put(String.valueOf(itemId), paymentJson);
                }
                
                JSONObject resp = ErrorResponse.success("Payment processed successfully");
                resp.put("amountPaid", remainingAmount);
                resp.put("paidAmount", newPaidAmount);
                resp.put("owedAmount", newOwedAmount);
                resp.put("owedAmountExcludingPaid", newOwedAmountExcludingPaid);
                resp.put("senderBalance", senderBalance);
                resp.put("itemPaymentInfo", itemPaymentInfo);
                
                ErrorResponse.sendJson(exchange, 200, resp);
                
            } catch (IllegalArgumentException e) {
                ErrorResponse.sendError(exchange, 400, e.getMessage());
            } catch (Exception e) {
                Logger.error(CONTEXT, "Error in PayReceiptHandler: " + e.getMessage(), e);
                ErrorResponse.sendError(exchange, 500, "Error processing payment: " + e.getMessage());
            }
        }
    }
}

