package cn.mahua.av.ad;

@SuppressWarnings("WeakerAccess")
public class AdBaseHtml {
    private static final String htmlStart =
            "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head lang=\"en\">\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "    <title>广告</title>\n" +
                    "<style>\n" +
                    "a,body,img {margin: 0;padding: 0; height: 100%; overflow: hidden;}\n" +
                    "a{display: block; overflow: hidden; padding: 0px; margin: 0px;}\n" +
                    "img{vertical-align:bottom;}\n" +
                    "a{-webkit-tap-highlight-color: rgba(0,0,0,0);}\n" +
                    "</style>\n" +
                    "</head>\n" +
                    "<body>\n";
    private static final String htmlEnd = "\n</body>\n" + "</html>";

    public static String getHtml(String s) {
        return htmlStart + s + htmlEnd;
    }

}
