package mchorse.bbs_ai.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ByteArrayType;
import mchorse.bbs_mod.data.types.ByteType;
import mchorse.bbs_mod.data.types.DoubleType;
import mchorse.bbs_mod.data.types.IntType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.LongType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;

import java.util.Base64;
import java.util.Map;

/**
 * BBS 数据树（{@link BaseType}）与 Gson JSON 的双向转换
 *
 * <p>用于把 {@code Form.toData()} 产出的 NBT 风格数据完整嵌入 .bbsm（GZIP + JSON）。
 * 转换保证往返一致性：
 * <ul>
 *   <li>Map/List/字符串/数值一一对应；数值经 {@link BaseType#equals(BaseType)} 的宽松
 *       数值比较语义判定相等（FloatType 0.5 与 DoubleType 0.5 等价）</li>
 *   <li>0/1 的 {@link ByteType}（BBS 用它存布尔）序列化为 JSON 布尔，还原为 ByteType</li>
 *   <li>字节数组序列化为 {@code {"__bytes": "<base64>"}}，整数数组为 {@code {"__ints": [...]}}，
 *       避免与普通字符串/列表混淆</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class DataJson
{
    /**
     * 字节数组包装对象的键
     */
    public static final String BYTES_KEY = "__bytes";

    /**
     * 整数数组包装对象的键
     */
    public static final String INTS_KEY = "__ints";

    /**
     * BaseType → JsonElement
     */
    public static JsonElement toJson(BaseType data)
    {
        if (data == null)
        {
            return null;
        }

        if (data.isMap())
        {
            JsonObject object = new JsonObject();

            for (Map.Entry<String, BaseType> entry : data.asMap())
            {
                object.add(entry.getKey(), toJson(entry.getValue()));
            }

            return object;
        }

        if (data.isList())
        {
            JsonArray array = new JsonArray();

            for (BaseType element : data.asList())
            {
                array.add(toJson(element));
            }

            return array;
        }

        if (data.isString())
        {
            return new JsonPrimitive(data.asString());
        }

        if (data instanceof ByteArrayType bytes)
        {
            JsonObject object = new JsonObject();

            object.addProperty(BYTES_KEY, Base64.getEncoder().encodeToString(bytes.value));

            return object;
        }

        if (data instanceof mchorse.bbs_mod.data.types.ShortArrayType shortArray)
        {
            JsonArray array = new JsonArray();

            for (short value : shortArray.value)
            {
                array.add(value);
            }

            return wrapInts(array);
        }

        if (data instanceof mchorse.bbs_mod.data.types.IntArrayType intArray)
        {
            JsonArray array = new JsonArray();

            for (int value : intArray.value)
            {
                array.add(value);
            }

            return wrapInts(array);
        }

        if (data instanceof mchorse.bbs_mod.data.types.LongArrayType longArray)
        {
            JsonArray array = new JsonArray();

            for (long value : longArray.value)
            {
                array.add(value);
            }

            return wrapInts(array);
        }

        if (data.isNumeric())
        {
            double value = data.asNumeric().doubleValue();

            /* ByteType(0/1) 是 BBS 的布尔存储 */
            if (data instanceof ByteType && (value == 0D || value == 1D))
            {
                return new JsonPrimitive(value == 1D);
            }

            return new JsonPrimitive(data.asNumeric().doubleValue());
        }

        return new JsonPrimitive(data.toString());
    }

    /**
     * JsonElement → BaseType
     */
    public static BaseType fromJson(JsonElement element)
    {
        if (element == null || element.isJsonNull())
        {
            return null;
        }

        if (element.isJsonObject())
        {
            JsonObject object = element.getAsJsonObject();

            if (object.size() == 1)
            {
                if (object.has(BYTES_KEY) && object.get(BYTES_KEY).isJsonPrimitive())
                {
                    try
                    {
                        return new ByteArrayType(Base64.getDecoder().decode(object.get(BYTES_KEY).getAsString()));
                    }
                    catch (IllegalArgumentException e)
                    {
                        return new MapType();
                    }
                }

                if (object.has(INTS_KEY) && object.get(INTS_KEY).isJsonArray())
                {
                    JsonArray array = object.getAsJsonArray(INTS_KEY);
                    long[] values = new long[array.size()];

                    for (int i = 0; i < values.length; i++)
                    {
                        values[i] = array.get(i).getAsLong();
                    }

                    return new mchorse.bbs_mod.data.types.LongArrayType(values);
                }
            }

            MapType map = new MapType();

            for (Map.Entry<String, JsonElement> entry : object.entrySet())
            {
                BaseType value = fromJson(entry.getValue());

                if (value != null)
                {
                    map.put(entry.getKey(), value);
                }
            }

            return map;
        }

        if (element.isJsonArray())
        {
            ListType list = new ListType();

            for (JsonElement item : element.getAsJsonArray())
            {
                BaseType value = fromJson(item);

                if (value != null)
                {
                    list.add(value);
                }
            }

            return list;
        }

        if (element.isJsonPrimitive())
        {
            JsonPrimitive primitive = element.getAsJsonPrimitive();

            if (primitive.isBoolean())
            {
                return new ByteType(primitive.getAsBoolean());
            }

            if (primitive.isNumber())
            {
                double value = primitive.getAsDouble();

                if (value == Math.rint(value) && !Double.isInfinite(value))
                {
                    if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE)
                    {
                        return new IntType((int) value);
                    }

                    return new LongType((long) value);
                }

                return new DoubleType((float) value);
            }

            return new StringType(primitive.getAsString());
        }

        return null;
    }

    private static JsonElement wrapInts(JsonArray array)
    {
        JsonObject object = new JsonObject();

        object.add(INTS_KEY, array);

        return object;
    }
}
