/*
 * Copyright (c) 2023, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.data.csv;

import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BStream;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;

/**
 * Native implementation of csv conversion.
 */
public class Native {

    /**
     * Converts a CSV string to a CSV Array.
     *
     * @param string
     * @param options
     * @return
     */
    public static Object fromCsvStringWithType(BString string, BMap<BString, Object> options, BTypedesc typed) {
        BArray value = ValueCreator.createArrayValue(new BString[0]);
        return ValueCreator.createArrayValue(new Object[]{value}, (ArrayType) value.getType());
    }

    public static Object fromCsvBytesWithType(BArray array, BMap<BString, Object> options, BTypedesc typed) {
        BArray value = ValueCreator.createArrayValue(new BString[0]);
        return ValueCreator.createArrayValue(new Object[]{value}, (ArrayType) value.getType());
    }

    public static Object fromCsvByteSteamWithType(BStream stream, BMap<BString, Object> options, BTypedesc typed) {
        BArray value = ValueCreator.createArrayValue(new BString[0]);
        return ValueCreator.createArrayValue(new Object[]{value}, (ArrayType) value.getType());
    }

    public static BString toString(Object string, BMap<BString, Object> options) {
        return null;
    }

}
