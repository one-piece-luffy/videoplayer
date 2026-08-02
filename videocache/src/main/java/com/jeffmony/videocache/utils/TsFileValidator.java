package com.jeffmony.videocache.utils;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class TsFileValidator {
    private static final String TAG="TsFileValidator";
    /**
     * ✅ 使用 FileChannel 验证TS文件是否有效（只检查开头和结尾）
     * ts文件以0x47开头
     */
    public static boolean isValidTsFile(File file) {
        long start=System.currentTimeMillis();
        if (!file.exists() || file.length() < 188) {
            return false;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel()) {

            long fileLength = file.length();
            ByteBuffer buffer = ByteBuffer.allocate(1);

            // 1. 检查开头第一个字节（位置0）
            channel.position(0);
            buffer.clear();
            if (channel.read(buffer) != 1 || buffer.get(0) != 0x47) {
                LogUtils.w(TAG, "Sync byte missing at start of file: " + file.getName());
                return false;
            }

            // 2. 如果文件大于188字节，检查结尾最后一个包的同步字节
            if (fileLength >= 188) {
                // 计算最后一个TS包的起始位置
                long lastPacketStart = (fileLength / 188 - 1) * 188;

                // 如果最后一个包的起始位置就是0（文件只有1个包），跳过检查
                if (lastPacketStart > 0) {
                    channel.position(lastPacketStart);
                    buffer.clear();
                    if (channel.read(buffer) != 1 || buffer.get(0) != 0x47) {
                        LogUtils.w(TAG, "Sync byte missing at end of file: " + file.getName() +
                                ", position=" + lastPacketStart);
                        return false;
                    }
                }
            }
            long end=System.currentTimeMillis();
            Log.e(TAG,"vali ts time;"+(end-start));
            return true;

        } catch (IOException e) {
            LogUtils.e(TAG, "Failed to validate TS file: " + file.getName(), e);
            return false;
        }
    }
}
