/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer3.source.rtsp;

import static com.google.android.exoplayer3.util.Assertions.checkArgument;
import static com.google.android.exoplayer3.util.Assertions.checkState;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer3.C;
import com.google.android.exoplayer3.upstream.DataSpec;
import com.google.android.exoplayer3.upstream.TransferListener;
import com.google.android.exoplayer3.upstream.UdpDataSource;
import com.google.android.exoplayer3.util.Util;
import java.io.IOException;

/** An {@link RtpDataChannel} for UDP transport. */
/* package */ final class UdpDataSourceRtpDataChannel implements RtpDataChannel {

  private static final String DEFAULT_UDP_TRANSPORT_FORMAT = "RTP/AVP;unicast;client_port=%d-%d";

  private final UdpDataSource dataSource;

  /** The associated RTCP channel; {@code null} if the current channel is an RTCP channel. */
  @Nullable private UdpDataSourceRtpDataChannel rtcpChannel;

  /** Creates a new instance. */
  public UdpDataSourceRtpDataChannel() {
    dataSource = new UdpDataSource();
  }

  @Override
  public String getTransport() {
    int dataPortNumber = getLocalPort();
    checkState(dataPortNumber != C.INDEX_UNSET); // Assert open() is called.
    return Util.formatInvariant(DEFAULT_UDP_TRANSPORT_FORMAT, dataPortNumber, dataPortNumber + 1);
  }

  @Override
  public int getLocalPort() {
    int port = dataSource.getLocalPort();
    return port == UdpDataSource.UDP_PORT_UNSET ? C.INDEX_UNSET : port;
  }

  @Override
  public void addTransferListener(TransferListener transferListener) {
    dataSource.addTransferListener(transferListener);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    return dataSource.open(dataSpec);
  }

  @Nullable
  @Override
  public Uri getUri() {
    return dataSource.getUri();
  }

  @Override
  public void close() {
    dataSource.close();

    if (rtcpChannel != null) {
      rtcpChannel.close();
    }
  }

  @Override
  public int read(byte[] target, int offset, int length) throws IOException {
    return dataSource.read(target, offset, length);
  }

  @Override
  public boolean usesSidebandBinaryData() {
    return false;
  }

  /**
   * Writing to a {@link UdpDataSource} backed {@link RtpDataChannel} is not supported at the
   * moment.
   */
  @Override
  public void write(byte[] buffer) {
    throw new UnsupportedOperationException();
  }

  public void setRtcpChannel(UdpDataSourceRtpDataChannel rtcpChannel) {
    checkArgument(this != rtcpChannel);
    this.rtcpChannel = rtcpChannel;
  }
}
