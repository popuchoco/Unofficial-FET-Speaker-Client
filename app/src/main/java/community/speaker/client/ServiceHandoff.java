package community.speaker.client;

import java.text.Normalizer;

enum ServiceHandoff {
    CHATGPT("https://chatgpt.com/", "已開啟 ChatGPT 官方入口，請在官方服務中自行登入與使用。"),
    GEMINI("https://gemini.google.com/app", "已開啟 Gemini 官方入口，請在官方服務中自行登入與對話。"),
    CLAUDE("https://claude.ai/", "已開啟 Claude 官方入口，請在官方服務中自行登入與對話。"),
    PUBLIC_NEWS("https://news.pts.org.tw/video/", "已開啟公視新聞影音。"),
    PUBLIC_LIVE("https://www.youtube.com/@PNNPTS/streams", "已開啟公視新聞網的直播頁面。"),
    TAXI("https://www.linego.me/taxi", "已開啟 LINE GO。請在官方服務確認地點與送出叫車。"),
    KKBOX("https://www.kkbox.com/tw/", "已開啟 KKBOX 官方服務，請在服務中選擇歌曲並開始播放。"),
    SPOTIFY("https://open.spotify.com/", "已開啟 Spotify 官方服務，請在服務中選擇歌曲並開始播放。");

    final String url;
    final String confirmation;

    ServiceHandoff(String url, String confirmation) {
        this.url = url;
        this.confirmation = confirmation;
    }

    static ServiceHandoff parse(String speech) {
        String command = SpeechPrefix.strip(Normalizer.normalize(speech, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}]", "").replaceFirst("[。！？?!]+$", "")
        );
        if (command.matches("(?:開啟|打開|進入|開|使用)(?i:CHATGPT)")) return CHATGPT;
        if (command.matches("(?:開啟|打開|進入|開|使用)(?i:GEMINI)(?:AI)?")) return GEMINI;
        if (command.matches("(?:開啟|打開|進入|開|使用)(?i:CLAUDE)")) return CLAUDE;
        if (command.matches("(開啟|打開)(?i:KKBOX)(?:音樂)?")) return KKBOX;
        if (command.matches("(開啟|打開)(?i:Spotify)(?:音樂)?")) return SPOTIFY;
        if (command.matches("(?:(開啟|打開)?(?i:LINEGO|TAXIGO)|叫(?i:TAXIGO))"
                + "|叫(一輛|一台)?計程車|叫車|搭計程車")) return TAXI;
        if (command.matches("(開啟|打開|播放|收看|看)?(公視新聞(網)?直播|公視直播新聞|直播新聞)")) {
            return PUBLIC_LIVE;
        }
        if (command.matches("(開啟|打開|播放|收看|看)公視新聞(網)?")) return PUBLIC_NEWS;
        return null;
    }
}
