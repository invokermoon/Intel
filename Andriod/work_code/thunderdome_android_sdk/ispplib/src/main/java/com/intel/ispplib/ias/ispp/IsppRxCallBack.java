/*
 * Copyright 2015 Intel Corporation
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

package com.intel.ispplib.ias.ispp;

public interface IsppRxCallBack {
    /**
     * Callback for established ispp connection
     *
     * @param mtu the maximum number of bytes in one ispp message
     */
    void onIsppConnectionEstablished(int mtu);

    /**
     * Callback called when ispp connection is lost
     */
    void onIsppConnectionLost();

    /**
     * Callback for new received ispp packets (usually for iasp)
     *
     * @param packet The received full ispp packet
     */
    void onIsppPacketReceived(byte[] packet);
}
