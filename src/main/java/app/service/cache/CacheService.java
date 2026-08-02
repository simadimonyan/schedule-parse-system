package app.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Slf4j
@Service
public class CacheService {

    private final CacheManager cacheManager;

    @Autowired
    public CacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void clearAllCaches() {
        var names = cacheManager.getCacheNames();
        log.info("Очистка кеша по ключам: {}", names);
        for (String name : names) {
            var cache = cacheManager.getCache(name);
            log.info("Чистка '{}'", name);
            cache.clear();
        }
        log.info("Кеш очищен");
    }

    /**
     * Очищает названные регионы целиком, не трогая остальные.
     *
     * <p>Нужно там, где ключ вычислить нельзя: выборка «группы третьего курса» собрана по
     * всему справочнику, и какая именно запись её испортила — по ключу не видно.
     */
    public void clear(Collection<String> regions) {
        for (String region : regions) {
            Cache cache = cacheManager.getCache(region);
            if (cache == null) continue;
            cache.clear();
        }
    }

    /** Убирает одну запись региона; отсутствующий регион или ключ — не ошибка. */
    public void evict(String region, Object key) {
        Cache cache = cacheManager.getCache(region);
        if (cache == null) return;
        cache.evict(key);
    }

}