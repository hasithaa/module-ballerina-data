package io.ballerina.stdlib.data.json;

import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.flags.SymbolFlags;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.MapType;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.TupleType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.UnionType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Traverse json tree.
 *
 * @since 0.1.0
 */
public class JsonTraverse {

    private static ThreadLocal<JsonTree> tlJsonTree = new ThreadLocal<>() {
        @Override
        protected JsonTree initialValue() {
            return new JsonTree();
        }
    };

    public static Object traverse(Object json, BTypedesc typed) {
        return traverse(json, typed.getDescribingType());
    }

    public static Object traverse(Object json, Type type) {
        JsonTree jsonTree = tlJsonTree.get();
        try {
            return jsonTree.traverseJson(json, type);
        } catch (JsonParser.JsonParserException e) {
            throw new RuntimeException(e);
        } finally {
            jsonTree.reset();
        }
    }


    static class JsonTree {

        Object currentJsonNode;
        Field currentField;
        Stack<Map<String, Field>> fieldHierarchy = new Stack<>();
        Stack<Type> restType = new Stack<>();
        Deque<Object> nodesStack = new ArrayDeque<>();
        Deque<String> fieldNames = new ArrayDeque<>();
        Type definedJsonType = PredefinedTypes.TYPE_JSON;
        ArrayType definedJsonArrayType = TypeCreator.createArrayType(definedJsonType);
        RecordType rootRecord;
        Type rootArray;

        void reset() {
            currentJsonNode = null;
            currentField = null;
            fieldHierarchy.clear();
            restType.clear();
            nodesStack.clear();
            fieldNames.clear();
            rootRecord = null;
            rootArray = null;
        }

        public Object traverseJson(Object json, Type type) throws JsonParser.JsonParserException {
            Type referredType = TypeUtils.getReferredType(type);
            switch (referredType.getTag()) {
                case TypeTags.JSON_TAG:
                case TypeTags.ANYDATA_TAG:
                case TypeTags.NULL_TAG:
                case TypeTags.BOOLEAN_TAG:
                case TypeTags.INT_TAG:
                case TypeTags.FLOAT_TAG:
                case TypeTags.DECIMAL_TAG:
                case TypeTags.STRING_TAG:
                    return JsonCreator.convertJSON(this, json, referredType);
                case TypeTags.RECORD_TYPE_TAG:
                    rootRecord = (RecordType) referredType;
                    this.fieldHierarchy.push(new HashMap<>(rootRecord.getFields()));
                    this.restType.push(rootRecord.getRestFieldType());
                    initializeRootObject(rootRecord);
                    traverseMapJsonOrArrayJson(json, referredType);
                    break;
                case TypeTags.ARRAY_TAG:
                case TypeTags.TUPLE_TAG:
                    rootArray = referredType;
                    initializeRootArray();
                    traverseMapJsonOrArrayJson(json, referredType);

                    if (nodesStack.isEmpty() || TypeUtils.getReferredType(
                            TypeUtils.getType(nodesStack.peek())).getTag() == TypeTags.RECORD_TYPE_TAG ||
                            TypeUtils.getReferredType(
                                    TypeUtils.getType(nodesStack.peek())).getTag() == TypeTags.MAP_TAG) {
                        currentJsonNode = JsonCreator.finalizeArray(this, referredType, (BArray) currentJsonNode);
                    }
                    break;
                case TypeTags.UNION_TAG:
                    for (Type memberType : ((UnionType) referredType).getMemberTypes()) {
                        try {
                            return traverseJson(json, memberType);
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    throw ErrorCreator.createError(StringUtils.fromString("incompatible type for json: " + type));
                default:
                    throw ErrorCreator.createError(StringUtils.fromString("incompatible type for json: " + type));
            }
            return currentJsonNode;
        }

        private void initializeRootObject(Type recordType) {
            if (recordType == null || recordType.getTag() != TypeTags.RECORD_TYPE_TAG) {
                throw ErrorCreator.createError(StringUtils.fromString("expected record type for input type"));
            }
            currentJsonNode = ValueCreator.createRecordValue((RecordType) recordType);
            nodesStack.push(currentJsonNode);
        }

        private void initializeRootArray() {
            if (rootArray == null) {
                throw ErrorCreator.createError(StringUtils.fromString("expected array type for input type"));
            }
            currentJsonNode = ValueCreator.createArrayValue(definedJsonArrayType);
            nodesStack.push(currentJsonNode);
        }

        private void traverseMapJsonOrArrayJson(Object json, Type type) throws JsonParser.JsonParserException {
            Object parentJsonNode = nodesStack.peek();
            if (json instanceof BMap) {
                traverseMapValue(json, parentJsonNode);
            } else if (json instanceof BArray) {
                traverseArrayValue(json, parentJsonNode);
            } else {
                // JSON value not compatible with map or array.
                if (type.getTag() == TypeTags.RECORD_TYPE_TAG) {
                    this.fieldHierarchy.pop();
                    this.restType.pop();
                }
                throw ErrorCreator.createError(StringUtils.fromString("incompatible type for json: " + json));
            }
            nodesStack.pop();
        }

        private void traverseMapValue(Object json, Object parentJsonNode) throws JsonParser.JsonParserException {
            BMap<BString, Object> map = (BMap<BString, Object>) json;
            for (BString key : map.getKeys()) {
                currentField = fieldHierarchy.peek().remove(key.toString());
                if (currentField == null) {
                    // Add to the rest field
                    if (restType.peek() != null) {
                        Type restFieldType = TypeUtils.getReferredType(restType.peek());
                        addRestField(restFieldType, key, map.get(key));
                    }
                    continue;
                }

                fieldNames.push(currentField.getFieldName());
                Type currentFieldType = TypeUtils.getReferredType(currentField.getFieldType());
                int currentFieldTypeTag = currentFieldType.getTag();
                Object mapValue = map.get(key);

                switch (currentFieldTypeTag) {
                    case TypeTags.MAP_TAG:
                        if (!checkTypeCompatibility(((MapType) currentFieldType).getConstrainedType(), mapValue)) {
                            throw ErrorCreator.createError(StringUtils.fromString("incompatible value '" + mapValue +
                                    "' for type '" + currentFieldType + "' in field '"
                                    + JsonCreator.getCurrentFieldPath(this)));
                        }
                        ((BMap<BString, Object>) currentJsonNode).put(StringUtils.fromString(fieldNames.pop()),
                                mapValue);
                        break;
                    case TypeTags.NULL_TAG:
                    case TypeTags.BOOLEAN_TAG:
                    case TypeTags.INT_TAG:
                    case TypeTags.FLOAT_TAG:
                    case TypeTags.DECIMAL_TAG:
                    case TypeTags.STRING_TAG:
                        Object value = JsonCreator.convertJSON(this, mapValue, currentFieldType);
                        ((BMap<BString, Object>) currentJsonNode).put(StringUtils.fromString(fieldNames.pop()),
                                value);
                        break;
                    default:
                        currentJsonNode = traverseJson(mapValue, currentFieldType);
                        ((BMap<BString, Object>) parentJsonNode).put(key, currentJsonNode);
                        currentJsonNode = parentJsonNode;
                }
            }
            Map<String, Field> currentField = fieldHierarchy.pop();
            checkOptionalFieldsAndLogError(currentField);
            restType.pop();
        }

        private void traverseArrayValue(Object json, Object parentJsonNode) throws JsonParser.JsonParserException {
            BArray array = (BArray) json;
            switch (rootArray.getTag()) {
                case TypeTags.ARRAY_TAG:
                    for (int i = 0; i < array.getLength(); i++) {
                        Object jsonMember = array.get(i);
                        currentJsonNode = traverseJson(jsonMember, ((ArrayType) rootArray).getElementType());
                        ((BArray) parentJsonNode).append(currentJsonNode);
                    }
                    break;
                case TypeTags.TUPLE_TAG:
                    Type restType = ((TupleType) rootArray).getRestType();
                    int expectedTupleTypeCount = ((TupleType) rootArray).getTupleTypes().size();
                    if (restType == null && expectedTupleTypeCount != array.getLength()) {
                        throw ErrorCreator.createError(StringUtils.fromString(
                                "size mismatch between target and source"));
                    }

                    for (int i = 0; i < array.getLength(); i++) {
                        Object jsonMember = array.get(i);
                        if (i < expectedTupleTypeCount) {
                            currentJsonNode = traverseJson(jsonMember, ((TupleType) rootArray).getTupleTypes().get(i));
                        } else {
                            currentJsonNode = traverseJson(jsonMember, restType);
                        }
                        ((BArray) parentJsonNode).append(currentJsonNode);
                    }
                    break;
            }
            currentJsonNode = parentJsonNode;
        }

        private void addRestField(Type restFieldType, BString key, Object jsonMember) {
            switch (restFieldType.getTag()) {
                case TypeTags.ANYDATA_TAG:
                case TypeTags.JSON_TAG:
                    ((BMap<BString, Object>) currentJsonNode).put(key, jsonMember);
                    break;
                case TypeTags.BOOLEAN_TAG:
                case TypeTags.INT_TAG:
                case TypeTags.FLOAT_TAG:
                case TypeTags.DECIMAL_TAG:
                case TypeTags.STRING_TAG:
                    if (checkTypeCompatibility(restFieldType, jsonMember)) {
                        ((BMap<BString, Object>) currentJsonNode).put(key, jsonMember);
                    }
                    break;
                default:
                    return;
            }
        }

        private boolean checkTypeCompatibility(Type constraintType, Object json) {
            if (json instanceof BMap) {
                BMap<BString, Object> map = (BMap<BString, Object>) json;
                for (BString key : map.getKeys()) {
                    if (!checkTypeCompatibility(constraintType, map.get(key))) {
                        return false;
                    }
                }
                return true;
            } else if ((json instanceof BString && constraintType.getTag() == TypeTags.STRING_TAG)
                    || (json instanceof Long && constraintType.getTag() == TypeTags.INT_TAG)
                    || (json instanceof Double && (constraintType.getTag() == TypeTags.FLOAT_TAG
                    || constraintType.getTag() == TypeTags.DECIMAL_TAG))
                    || (Boolean.class.isInstance(json) && constraintType.getTag() == TypeTags.BOOLEAN_TAG)
                    || (json == null && constraintType.getTag() == TypeTags.NULL_TAG)) {
                return true;
            } else {
                return false;
            }
        }

        private void checkOptionalFieldsAndLogError(Map<String, Field> currentField) {
            for (Field field : currentField.values()) {
                if (SymbolFlags.isFlagOn(field.getFlags(), SymbolFlags.REQUIRED)) {
                    throw ErrorCreator.createError(StringUtils.fromString("required field '" + field.getFieldName() +
                            "' not present in JSON"));
                }
            }
        }
    }
}
