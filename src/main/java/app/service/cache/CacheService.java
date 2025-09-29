package app.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

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
            log.info("Чистка '{}', native cache = {}", name, cache.getNativeCache());

        }
        log.info("Кеш очищен");
    }

}