package com.baofu.videoplayer.utils;


public enum LocalLink {
    link1("我们的少年时代", "https://v.lzcdn27.com/20260816/15488_96a2d3f0/index.m3u8"),
    link2("师兄太稳健", "https://v.lzcdn27.com/20260819/15635_39f8c15a/index.m3u8"),
    link3("仗剑青山", "https://hn.bfvvs.com/play/aKrLXOne/index.m3u8"),
    link4("遮天", "https://play.xluuss.com/play/Ddw26vXb/index.m3u8");

    //2.私有化常量类型
    public final String LINK;
    public final String NAME;

    //3.提供枚举形参构造器
    LocalLink(String NAME, String link) {
        this.LINK = link;
        this.NAME = NAME;
    }

}
