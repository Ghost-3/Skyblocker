package me.xmrvizzy.skyblocker.chat.filters;

import me.xmrvizzy.skyblocker.chat.ChatListenerTest;
import org.junit.jupiter.api.Test;

class ImplosionFilterTest extends ChatListenerTest<ImplosionFilter> {
    public ImplosionFilterTest() {
        super(new ImplosionFilter());
    }

    @Test
    void oneEnemy() {
        assertMatches("Your Implosion hit 1 enemy for 636,116.8 damage.");
    }

    @Test
    void multipleEnemies() {
        assertMatches("Your Implosion hit 7 enemies for 4,452,817.4 damage.");
    }
}