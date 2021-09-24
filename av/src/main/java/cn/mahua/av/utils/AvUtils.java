package cn.mahua.av.utils;

import java.text.DecimalFormat;

public class AvUtils {
    public static String getSizeStr(long size) {
        StringBuffer sb = new StringBuffer();
        DecimalFormat format = new DecimalFormat("###.00");
        if (size >= 1024 * 1024 * 1024) {
            double i = (size / (1024.0 * 1024.0 * 1024.0));
            sb.append(format.format(i)).append("GB");
        } else if (size >= 1024 * 1024) {
            double i = (size / (1024.0 * 1024.0));
            sb.append(format.format(i)).append("MB");
        } else if (size >= 1024) {
            double i = (size / (1024.0));
            sb.append(format.format(i)).append("KB");
        } else if (size < 1024) {
            if (size <= 0) {
                sb.append("0B");
            } else {
                sb.append((int) size).append("B");
            }
        }
        return sb.toString();
    }
}
