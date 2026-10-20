package com.wenwen.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 统计 / 列表类只读接口的短时缓存（看板、患者列表、项目总览、数据质控、随访计划）。
 * 本平台的写接口完成后整体清空（见 ReadCacheConfiguration）；老系统直接改库的数据最多晚 ra.read-cache.seconds 秒（默认 60）出现。
 * ra.read-cache.seconds=0 关闭缓存。缓存的对象只读，调用方不能修改。
 */
@Component
public class ReadCache {

	/** 最多缓存多少条（按医生 + 查询条件），超过就整体清空，避免占内存 */
	private static final int MAX_ENTRIES = 500;

	private static final class Entry {
		final long expiresAt;
		final Object value;

		Entry(long expiresAt, Object value) {
			this.expiresAt = expiresAt;
			this.value = value;
		}
	}

	/** 没有注入 ReadCache 的环境（如只注册部分 bean 的测试上下文）用它：不缓存，直接计算 */
	public static final ReadCache DISABLED = new ReadCache();

	private final Map<String, Entry> entries = new ConcurrentHashMap<>();
	/** 每次清空加 1；读的过程中发生过清空，结果就不放进缓存（避免写之前开始的读把旧数据放回去） */
	private final AtomicLong generation = new AtomicLong();

	@Value("${ra.read-cache.seconds:60}")
	private long seconds;

	@SuppressWarnings("unchecked")
	public <T> T get(String key, Supplier<T> loader) {
		if (seconds <= 0) {
			return loader.get();
		}
		long now = System.currentTimeMillis();
		Entry e = entries.get(key);
		if (e != null && e.expiresAt > now) {
			return (T) e.value;
		}
		long gen = generation.get();
		T value = loader.get();
		if (entries.size() >= MAX_ENTRIES) {
			entries.clear();
		}
		if (gen == generation.get()) {
			entries.put(key, new Entry(now + seconds * 1000, value));
		}
		return value;
	}

	public void clear() {
		generation.incrementAndGet();
		entries.clear();
	}
}
