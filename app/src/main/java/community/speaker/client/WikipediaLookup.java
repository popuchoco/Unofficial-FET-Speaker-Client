package community.speaker.client;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WikipediaLookup {
    private static final String HOST = "https://zh.wikipedia.org";
    private static final Pattern ASK = Pattern.compile(
            "^(?:(?:請幫我|幫我|請)\\s*)?(?:(?:搜尋|查詢|查一下|查)\\s*)?(?:維基百科|維基)(?:\\s*(?:搜尋|查詢|查一下|查))?[：:]?\\s*(.*)$",
            Pattern.DOTALL);

    private WikipediaLookup() {}

    static String topic(String input) {
        Matcher match = ASK.matcher(input.trim());
        if (!match.matches()) return null;
        return match.group(1).trim().replaceFirst("[。？！?!]+$", "").trim();
    }

    static LookupAnswer search(String topic) throws IOException, JSONException {
        JSONObject results = get(HOST + "/w/rest.php/v1/search/page?q="
                + URLEncoder.encode(topic, "UTF-8") + "&limit=3");
        JSONArray pages = results.getJSONArray("pages");
        if (pages.length() == 0) return new LookupAnswer("維基百科找不到符合的詞條。", null);

        JSONObject page = choose(pages, topic);
        if (page == null) return alternatives(pages);
        long id = page.getLong("id");
        String source = HOST + "/w/index.php?curid=" + id + "&variant=zh-tw";
        JSONObject query = get(HOST + "/w/api.php?action=query&format=json&formatversion=2"
                + "&variant=zh-tw&prop=extracts%7Cpageprops&ppprop=disambiguation"
                + "&exintro=1&explaintext=1&exchars=260&pageids=" + id);
        JSONObject article = query.getJSONObject("query").getJSONArray("pages").getJSONObject(0);
        JSONObject properties = article.optJSONObject("pageprops");
        if (properties != null && properties.has("disambiguation")) {
            return new LookupAnswer("這是消歧義詞條，請用更完整的名稱重新查詢。", source);
        }
        String snippet = article.isNull("extract") ? "" : article.optString("extract").trim();
        if (snippet.isEmpty() && !page.isNull("description")) snippet = page.optString("description").trim();
        if (snippet.isEmpty()) {
            return new LookupAnswer("找到維基百科詞條「" + article.getString("title") + "」，目前沒有可播報的摘要。", source);
        }
        return new LookupAnswer("根據維基百科，" + snippet, source);
    }

    private static JSONObject choose(JSONArray pages, String topic) throws JSONException {
        for (int index = 0; index < pages.length(); index++) {
            JSONObject page = pages.getJSONObject(index);
            if (matchTitle(page.getString("title")).equalsIgnoreCase(matchTitle(topic))) return page;
        }
        return pages.length() == 1 ? pages.getJSONObject(0) : null;
    }

    private static String matchTitle(String title) {
        return title.replaceAll("[\\s\\p{Z}_]", "");
    }

    private static LookupAnswer alternatives(JSONArray pages) throws JSONException {
        java.util.StringJoiner names = new java.util.StringJoiner("、");
        for (int index = 0; index < pages.length(); index++) {
            names.add(pages.getJSONObject(index).getString("title"));
        }
        return new LookupAnswer("找到多個詞條，請用完整名稱重新查詢。" + names + "。", null);
    }

    private static JSONObject get(String url) throws IOException, JSONException {
        return new JSONObject(PublicDataClient.get(url, "application/json", 128 * 1024));
    }
}