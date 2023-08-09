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

package io.ballerina.stdlib.data.json;

import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BStream;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;
import io.ballerina.stdlib.data.utils.DataUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;

/**
 * JsonToJson.
 *
 * @since 0.1.0
 */
public class Native {

    public static Object fromJsonByteArrayWithType(BArray byteArr, BMap<BString, Object> map, BTypedesc typed) {
        byte[] bytes = byteArr.getBytes();
        try {
            return JsonParser.parse(new InputStreamReader(new ByteArrayInputStream(bytes)), typed);
        } catch (Exception e) {
            return DataUtils.getJsonError(e.getMessage());
        }
    }

    public static Object fromJsonByteStreamWithType(BStream byteStream, BMap<BString, Object> map, BTypedesc typed) {
        return null;
    }

    public static Object fromJsonWithType(Object json, BMap<BString, Object> map, BTypedesc typed) {
        try {
            return JsonTraverse.traverse(json, typed);
        } catch (Exception e) {
            return DataUtils.getJsonError(e.getMessage());
        }
    }

    public static Object fromJsonStringWithType(BString json, BMap<BString, Object> map, BTypedesc typed) {
        try {
            return JsonParser.parse(new StringReader(json.getValue()), typed);
        } catch (Exception e) {
            return DataUtils.getJsonError(e.getMessage());
        }
    }
}
