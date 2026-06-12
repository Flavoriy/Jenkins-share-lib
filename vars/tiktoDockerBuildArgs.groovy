def call() {
    return [
        '--build-arg DATABASE_URL',
        '--build-arg NEXT_PUBLIC_APP_URL',
        '--build-arg NEXT_PUBLIC_SUPABASE_URL',
        '--build-arg NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY',
        '--build-arg NEXT_PUBLIC_SUPABASE_ANON_KEY'
    ]
}
