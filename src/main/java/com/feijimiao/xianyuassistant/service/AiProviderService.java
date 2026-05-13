package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuAiProvider;

import java.util.List;

public interface AiProviderService {

    List<XianyuAiProvider> listAll();

    XianyuAiProvider getById(Long id);

    XianyuAiProvider getActiveProvider();

    void save(XianyuAiProvider provider);

    void deleteById(Long id);

    void activate(Long id);
}
