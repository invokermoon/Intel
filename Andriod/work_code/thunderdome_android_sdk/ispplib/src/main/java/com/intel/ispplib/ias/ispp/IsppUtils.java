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

import java.util.UUID;

public class IsppUtils {

    public enum ISPP_STATE {
        INITIAL_STATE,
        WAITING_FOR_LINK_SETUP,
        LINK_OK,
        TRANSMITTING_PACKET,
        LINK_DOWN,
        WAITING_FOR_ACK,
        RETRANSMITTING_PACKET,
        TRANSMITTING_CONTROL
    }

    public static final UUID ISSP_SERVICE_UUID = UUID.
            fromString("dd97c415-fed9-4766-b18f-ba690d24a06a");
    public static final UUID ISSP_CONTROL_CHAR = UUID.
            fromString("dd97c416-fed9-4766-b18f-ba690d24a06a");
    public static final UUID ISSP_DATA_CHAR = UUID.
            fromString("dd97cf01-fed9-4766-b18f-ba690d24a06a");
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.
            fromString("00002902-0000-1000-8000-00805f9b34fb");

}
