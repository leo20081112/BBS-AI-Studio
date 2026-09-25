package mchorse.bbs_ai.core;

import mchorse.bbs_mod.data.types.MapType;

/**
 * AI 配置序列化辅助
 *
 * <p>将 {@link AIConfig} 与 BBS 数据系统（MapType）互相转换。序列化键名与外部
 * Python 工具链的 snake_case 契约保持一致。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public final class AIConfigCodec
{
    private AIConfigCodec()
    {}

    /**
     * 将配置写入 MapType（snake_case 键名，与 Python 工具链契约一致）
     */
    public static void toData(AIConfig config, MapType map)
    {
        map.putString("mode", config.getMode().name());
        map.putString("provider", config.getProvider().name());
        map.putString("api_key", config.getApiKey());
        map.putString("base_url", config.getBaseUrl());
        map.putString("model", config.getModel());
        map.putFloat("temperature", config.getTemperature());
        map.putInt("max_tokens", config.getMaxTokens());
    }

    /**
     * 从 MapType 恢复配置（缺失字段保留默认值）
     */
    public static void fromData(AIConfig config, MapType map)
    {
        if (map == null || map.isEmpty())
        {
            return;
        }

        try
        {
            config.setMode(AIConfig.Mode.valueOf(map.getString("mode", "API").toUpperCase()));
        }
        catch (IllegalArgumentException e)
        {
            config.setMode(AIConfig.Mode.API);
        }

        try
        {
            config.setProvider(AIConfig.Provider.valueOf(map.getString("provider", "OPENAI").toUpperCase()));
        }
        catch (IllegalArgumentException e)
        {
            config.setProvider(AIConfig.Provider.OPENAI);
        }

        config.setApiKey(map.getString("api_key", ""));
        config.setBaseUrl(map.getString("base_url", ""));
        config.setModel(map.getString("model", ""));
        config.setTemperature(map.getFloat("temperature", 0.7F));
        config.setMaxTokens(map.getInt("max_tokens", 4096));
    }
}
