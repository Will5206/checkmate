// Default package (no package declaration)

import models.Friend;
import services.FriendService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FriendServiceTest {

    @Test
    void testAddFriendship() {
        FriendService service = new FriendService();
        Friend f = service.addFriendship(1, 2);

        assertNotNull(f);
        assertEquals(1, f.getUserId1());
        assertEquals(2, f.getUserId2());

        // both users should list each other as friends
        assertTrue(service.listFriends(1).contains(2));
        assertTrue(service.listFriends(2).contains(1));
    }

    @Test
    void testRemoveFriendship() {
        FriendService service = new FriendService();
        service.addFriendship(1, 2);
        boolean removed = service.removeFriendship(1, 2);

        assertTrue(removed);
        assertFalse(service.listFriends(1).contains(2));
        assertFalse(service.listFriends(2).contains(1));
    }

    @Test
    void testListFriends() {
        FriendService service = new FriendService();
        service.addFriendship(1, 2);
        service.addFriendship(1, 3);

        List<Integer> friends = service.listFriends(1);
        assertEquals(2, friends.size());
        assertTrue(friends.containsAll(List.of(2, 3)));
    }

    @Test
    void testFriendStatusChange() {
        Friend f = new Friend(1, 1, 2, new java.util.Date(), "pending");
        f.acceptRequest();
        assertEquals("accepted", f.getStatus());

        f.declineRequest();
        assertEquals("declined", f.getStatus());
    }
}
