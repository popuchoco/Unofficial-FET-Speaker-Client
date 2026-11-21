package community.speaker.client;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ServiceHandoffTest {
    @Test public void recognizesExplicitRequestsForOfficialServices() {
        assertEquals(ServiceHandoff.TAXI, ServiceHandoff.parse("請幫我叫一台計程車。"));
        assertEquals(ServiceHandoff.TAXI, ServiceHandoff.parse("開啟 Line Go"));
        assertEquals(ServiceHandoff.PUBLIC_LIVE, ServiceHandoff.parse("播放公視新聞直播"));
        assertEquals(ServiceHandoff.PUBLIC_NEWS, ServiceHandoff.parse("播放公視新聞"));
        assertEquals(ServiceHandoff.KKBOX, ServiceHandoff.parse("開啟 KKBOX 音樂"));
        assertEquals(ServiceHandoff.SPOTIFY, ServiceHandoff.parse("打開 Spotify"));
    }

    @Test public void ignoresQuestionsAndOtherCommands() {
        assertNull(ServiceHandoff.parse("叫車多少錢"));
        assertNull(ServiceHandoff.parse("不要叫車"));
        assertNull(ServiceHandoff.parse("三分鐘後提醒我看公視新聞"));
        assertNull(ServiceHandoff.parse("查維基百科公視新聞"));
        assertNull(ServiceHandoff.parse("播放周杰倫的歌"));
    }
}
