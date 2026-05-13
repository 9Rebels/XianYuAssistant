package com.feijimiao.xianyuassistant.utils;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetSocketAddress;
import java.net.Proxy;

@Slf4j
@Component
public class AccountProxyHelper {

    @Autowired
    private XianyuAccountMapper accountMapper;

    public Proxy resolveProxy(Long accountId) {
        if (accountId == null) {
            return Proxy.NO_PROXY;
        }
        XianyuAccount account = accountMapper.selectById(accountId);
        return resolveProxyFromAccount(account);
    }

    public Proxy resolveProxyFromAccount(XianyuAccount account) {
        if (account == null || !StringUtils.hasText(account.getProxyType()) || !StringUtils.hasText(account.getProxyHost())) {
            return Proxy.NO_PROXY;
        }
        Integer port = account.getProxyPort();
        if (port == null || port <= 0) {
            return Proxy.NO_PROXY;
        }
        Proxy.Type type = "socks5".equalsIgnoreCase(account.getProxyType()) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        return new Proxy(type, new InetSocketAddress(account.getProxyHost(), port));
    }

    public Authenticator resolveProxyAuthenticator(Long accountId) {
        if (accountId == null) {
            return Authenticator.NONE;
        }
        XianyuAccount account = accountMapper.selectById(accountId);
        return resolveProxyAuthenticatorFromAccount(account);
    }

    public Authenticator resolveProxyAuthenticatorFromAccount(XianyuAccount account) {
        if (account == null || !StringUtils.hasText(account.getProxyUsername())) {
            return Authenticator.NONE;
        }
        String username = account.getProxyUsername();
        String password = account.getProxyPassword() != null ? account.getProxyPassword() : "";
        return (route, response) -> {
            if (response.request().header("Proxy-Authorization") != null) {
                return null;
            }
            return response.request().newBuilder()
                    .header("Proxy-Authorization", Credentials.basic(username, password))
                    .build();
        };
    }

    public OkHttpClient.Builder applyProxy(OkHttpClient.Builder builder, Long accountId) {
        Proxy proxy = resolveProxy(accountId);
        if (proxy != Proxy.NO_PROXY) {
            builder.proxy(proxy);
            Authenticator auth = resolveProxyAuthenticator(accountId);
            if (auth != Authenticator.NONE) {
                builder.proxyAuthenticator(auth);
            }
            log.debug("账号[{}]使用代理: {}", accountId, proxy.address());
        }
        return builder;
    }

    public OkHttpClient createHttpClient(SessionCookieJar cookieJar, Long accountId) {
        Proxy proxy = resolveProxy(accountId);
        if (proxy == Proxy.NO_PROXY) {
            return cookieJar.createHttpClient();
        }
        Authenticator auth = resolveProxyAuthenticator(accountId);
        return cookieJar.createHttpClient(proxy, auth);
    }
}
