package patterns;

import models.Receipt;
import java.util.ArrayList;
import java.util.List;

// Subject class in the Observer pattern to manage observers
public class ReceiptSubject {
    private List<ReceiptObserver> observers = new ArrayList<>();

    public void addObserver(ReceiptObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(ReceiptObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(Receipt receipt, String message) {
        for (ReceiptObserver observer : observers) {
            observer.update(receipt, message);
        }
    }
}
