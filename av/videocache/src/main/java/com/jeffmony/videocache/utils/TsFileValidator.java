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
        if (!file.exists() || file.length() < 188) {
            LogUtils.w(TAG, "File too small: " + file.length());
            return false;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel()) {

            long fileLength = file.length();
            ByteBuffer buffer = ByteBuffer.allocate(1);

            // ✅ 1. 检测TS包长度（188/192/204）
            int packetSize = detectPacketSize(channel, fileLength);
            if (packetSize == -1) {
                LogUtils.e(TAG, "Cannot detect packet size");
                return false;
            }
            LogUtils.d(TAG, "Detected packet size: " + packetSize);

            // ✅ 2. 验证所有包（采样验证，兼顾性能）
            int packetCount = (int) (fileLength / packetSize);

            // 如果包数量太少，全量验证
            if (packetCount <= 20) {
                for (int i = 0; i < packetCount; i++) {
                    long pos = i * packetSize;
                    channel.position(pos);
                    buffer.clear();
                    if (channel.read(buffer) != 1 || buffer.get(0) != 0x47) {
                        LogUtils.e(TAG, "Sync byte missing at packet " + i + ", pos=" + pos);
                        return false;
                    }
                }
                return true;
            }

            // ✅ 3. 采样验证（包数量多时）
            // 检查开头20个包
            for (int i = 0; i < Math.min(20, packetCount); i++) {
                long pos = i * packetSize;
                channel.position(pos);
                buffer.clear();
                if (channel.read(buffer) != 1 || buffer.get(0) != 0x47) {
                    LogUtils.e(TAG, "Sync byte missing at start packet " + i + ", pos=" + pos);
                    return false;
                }
            }

            // 检查中间20个包（均匀分布）
            for (int i = 0; i < 20; i++) {
                int index = (int) (packetCount * (i + 1) / 21.0);
                if (index >= packetCount) continue;
                long pos = index * packetSize;
                channel.position(pos);
                buffer.clear();
                if (channel.read(buffer) != 1 || buffer.get(0) != 0x47) {
                    LogUtils.e(TAG, "Sync byte missing at mid packet " + index + ", pos=" + pos);
                    return false;
                }
            }

            // 检查最后20个包
            for (int i = Math.max(0, packetCount - 20); i < packetCount; i++) {
                long pos = i * packetSize;
                channel.position(pos);
                buffer.clear();
                if (channel.read(buffer) != 1 || buffer.get(0) != 0x47) {
                    LogUtils.e(TAG, "Sync byte missing at end packet " + i + ", pos=" + pos);
                    return false;
                }
            }

            // ✅ 4. 检查最后一个包是否完整
            if (fileLength % packetSize != 0) {
                LogUtils.w(TAG, "File not aligned to packet size: " +
                        fileLength % packetSize + " bytes remaining");
                // 检查剩余数据是否都是0或垃圾数据
                long lastPacketStart = (fileLength / packetSize) * packetSize;
                if (lastPacketStart < fileLength) {
                    channel.position(lastPacketStart);
                    buffer.clear();
                    if (channel.read(buffer) == 1 && buffer.get(0) != 0x47) {
                        // 剩余数据不完整，但可能不影响播放
                        LogUtils.w(TAG, "Trailing data after last packet: " +
                                (fileLength - lastPacketStart) + " bytes");
                    }
                }
            }

            return true;

        } catch (IOException e) {
            LogUtils.e(TAG, "Validation error", e);
            return false;
        }
    }
    /**
     * ✅ 检测TS包长度（188/192/204）
     * 返回 -1 表示无法检测
     */
    private static int detectPacketSize(FileChannel channel, long fileLength) throws IOException {
        // 尝试常见的TS包长度
        int[] possibleSizes = {188, 192, 204};
        ByteBuffer buffer = ByteBuffer.allocate(1);

        for (int size : possibleSizes) {
            if (fileLength < size * 5) continue; // 至少需要5个包

            boolean isValid = true;

            // 检查前5个包的同步字节
            for (int i = 0; i < 5; i++) {
                long pos = i * size;
                channel.position(pos);
                buffer.clear();
                if (channel.read(buffer) != 1 || buffer.get(0) != 0x47) {
                    isValid = false;
                    break;
                }
            }

            if (isValid) {
                // 再检查中间和结尾的几个包，确认不是巧合
                int midIndex = (int) (fileLength / size / 2);
                long midPos = midIndex * size;
                channel.position(midPos);
                buffer.clear();
                if (channel.read(buffer) == 1 && buffer.get(0) == 0x47) {
                    // 中间位置也找到了，更可靠
                    return size;
                }

                // 如果中间没找到，可能只是开头巧合，继续尝试其他长度
                continue;
            }
        }

        // ✅ 如果标准长度都检测不到，尝试搜索第一个0x47的位置
        // 某些TS文件可能在开头有一些额外数据
        return detectPacketSizeBySearch(channel, fileLength);
    }

    /**
     * ✅ 通过搜索0x47来检测包长度
     */
    private static int detectPacketSizeBySearch(FileChannel channel, long fileLength) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        int searchLimit = (int) Math.min(2048, fileLength);

        // 搜索第一个0x47的位置
        int firstSyncPos = -1;
        for (int i = 0; i < searchLimit; i++) {
            channel.position(i);
            buffer.clear();
            if (channel.read(buffer) == 1 && buffer.get(0) == 0x47) {
                firstSyncPos = i;
                break;
            }
        }

        if (firstSyncPos == -1) {
            return -1;
        }

        // 在第一个0x47之后，尝试检测包长度
        int[] possibleSizes = {188, 192, 204};
        for (int size : possibleSizes) {
            if (firstSyncPos + size * 5 > fileLength) continue;

            boolean isValid = true;
            for (int i = 1; i < 5; i++) {
                long pos = firstSyncPos + i * size;
                channel.position(pos);
                buffer.clear();
                if (channel.read(buffer) != 1 || buffer.get(0) != 0x47) {
                    isValid = false;
                    break;
                }
            }

            if (isValid) {
                LogUtils.d(TAG, "Detected packet size " + size + " with offset " + firstSyncPos);
                return size;
            }
        }

        return -1;
    }
}
