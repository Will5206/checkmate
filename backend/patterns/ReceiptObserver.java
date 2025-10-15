
// Used to notify users when a receipt is added to their account
// Uses interface in case we want to implement different types of observers in the future(ex: group chats)
public interface ReceiptObserver {
    void update(Receipt receipt, String message);
}
