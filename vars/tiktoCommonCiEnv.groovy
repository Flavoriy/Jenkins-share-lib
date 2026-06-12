def call(Map config = [:]) {
    List<String> values = [
        'CI=true',
        'NEXT_TELEMETRY_DISABLED=1',
        'DATABASE_URL=postgresql://ci:ci@localhost:5432/tikto?schema=public',
        'NEXT_PUBLIC_APP_URL=http://localhost:3000',
        'NEXT_PUBLIC_SUPABASE_URL=https://example.supabase.co',
        'NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=ci-publishable-key',
        'TOKEN_ENCRYPTION_KEY=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE='
    ]

    values.addAll(ConfigValidator.normalizeList(config.extraEnv ?: config.commonEnv))
    return values
}
