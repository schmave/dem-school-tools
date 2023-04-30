package com.feth.play.module.pa;

import com.feth.play.module.pa.exceptions.AuthException;
import com.feth.play.module.pa.providers.AuthProvider;
import com.feth.play.module.pa.service.UserService;
import com.feth.play.module.pa.user.AuthUser;
import com.typesafe.config.Config;
import play.Logger;
import play.cache.SyncCacheApi;
import play.i18n.Lang;
import play.i18n.MessagesApi;
import play.mvc.Call;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.Session;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

import static play.libs.F.Tuple;

@Singleton
public class PlayAuthenticate {
  static Logger.ALogger sLogger = Logger.of("application");

  public static final String SETTING_KEY_PLAY_AUTHENTICATE = "play-authenticate";
  private static final String SETTING_KEY_AFTER_AUTH_FALLBACK = "afterAuthFallback";
  private static final String SETTING_KEY_AFTER_LOGOUT_FALLBACK = "afterLogoutFallback";
  private static final String SETTING_KEY_ACCOUNT_MERGE_ENABLED = "accountMergeEnabled";
  private static final String SETTING_KEY_ACCOUNT_AUTO_LINK = "accountAutoLink";

  private final List<Lang> preferredLangs;
  private final Config config;

  @Inject
  public PlayAuthenticate(
      final Config config,
      final Resolver resolver,
      final MessagesApi messagesApi,
      final SyncCacheApi cacheApi) {
    this.config = config;
    this.resolver = resolver;
    this.messagesApi = messagesApi;
    this.cacheApi = cacheApi;

    Locale englishLocale = new Locale("en");
    Lang englishLang = new Lang(englishLocale);
    preferredLangs = Collections.singletonList(englishLang);
  }

  private final Resolver resolver;
  private final MessagesApi messagesApi;
  private final SyncCacheApi cacheApi;

  public Resolver getResolver() {
    return resolver;
  }

  private UserService userService;

  public void setUserService(final UserService service) {
    userService = service;
  }

  public UserService getUserService() {
    if (userService == null) {
      throw new RuntimeException(
          messagesApi
              .preferred(preferredLangs)
              .at("playauthenticate.core.exception.no_user_service"));
    }
    return userService;
  }

  private static final String ORIGINAL_URL = "pa.url.orig";
  private static final String USER_KEY = "pa.u.id";
  private static final String PROVIDER_KEY = "pa.p.id";
  private static final String EXPIRES_KEY = "pa.u.exp";
  private static final String SESSION_ID_KEY = "pa.s.id";

  public Config getConfiguration() {
    return config.getConfig(SETTING_KEY_PLAY_AUTHENTICATE);
  }

  public static final long TIMEOUT = 10L * 1000;

  public Session storeOriginalUrl(final Http.Request request) {
    Session session = request.session();
    String loginUrl = null;
    if (this.getResolver().login() != null) {
      loginUrl = this.getResolver().login().url();
    } else {
      sLogger.warn("You should define a login call in the resolver");
    }

    if (request.method().equals("GET") && !request.path().equals(loginUrl)) {
      sLogger.debug(
          "Path where we are coming from ("
              + request.uri()
              + ") is different than the login URL ("
              + loginUrl
              + ")");
      session = session.adding(PlayAuthenticate.ORIGINAL_URL, request.uri());
    } else {
      sLogger.debug("The path we are coming from is the Login URL - delete jumpback");
      session = session.removing(PlayAuthenticate.ORIGINAL_URL);
    }
    return session;
  }

  public Session storeUser(Session session, final AuthUser authUser) {
    // User logged in once more - wanna make some updates?
    final AuthUser u = getUserService().update(authUser);

    session = session.adding(PlayAuthenticate.USER_KEY, u.getId());
    session = session.adding(PlayAuthenticate.PROVIDER_KEY, u.getProvider());
    if (u.expires() != AuthUser.NO_EXPIRATION) {
      session = session.adding(EXPIRES_KEY, Long.toString(u.expires()));
    } else {
      session = session.removing(EXPIRES_KEY);
    }
    return session;
  }

  public boolean isLoggedIn(final Session session) {
    boolean ret =
        session.get(USER_KEY).isPresent() // user is set
            && session.get(PROVIDER_KEY).isPresent(); // provider is set
    ret &= AuthProvider.Registry.hasProvider(session.get(PROVIDER_KEY).orElse("")); // this
    // provider
    // is
    // active
    if (session.get(EXPIRES_KEY).isPresent()) {
      // expiration is set
      final long expires = getExpiration(session);
      if (expires != AuthUser.NO_EXPIRATION) {
        ret &= (new Date()).getTime() < expires; // and the session
        // expires after now
      }
    }
    return ret;
  }

  public Result logout(final Session session) {
    return Controller.redirect(
            getUrl(getResolver().afterLogout(), SETTING_KEY_AFTER_LOGOUT_FALLBACK))
        .withSession(session.removing(USER_KEY, PROVIDER_KEY, EXPIRES_KEY, ORIGINAL_URL));
  }

  public boolean hasUserService() {
    return userService != null;
  }

  private long getExpiration(final Session session) {
    long expires;
    if (session.get(EXPIRES_KEY).isPresent()) {
      try {
        expires = Long.parseLong(session.get(EXPIRES_KEY).orElse(""));
      } catch (final NumberFormatException nfe) {
        expires = AuthUser.NO_EXPIRATION;
      }
    } else {
      expires = AuthUser.NO_EXPIRATION;
    }
    return expires;
  }

  public AuthUser getUser(final Session session) {
    final String provider = session.get(PROVIDER_KEY).orElse(null);
    final String id = session.get(USER_KEY).orElse(null);
    final long expires = getExpiration(session);

    if (provider != null && id != null) {
      return getProvider(provider).getSessionAuthUser(id, expires);
    } else {
      return null;
    }
  }

  public boolean isAccountAutoLink() {
    return getConfiguration().getBoolean(SETTING_KEY_ACCOUNT_AUTO_LINK);
  }

  public boolean isAccountMergeEnabled() {
    return getConfiguration().getBoolean(SETTING_KEY_ACCOUNT_MERGE_ENABLED);
  }

  public Session storeInCache(final Session session, final String key, final Object o) {
    Tuple<String, Session> result = getCacheKey(session, key);
    cacheApi.set(result._1, o);
    return result._2;
  }

  public void removeFromCache(final Session session, final String key) {
    final String k = getCacheKey(session, key)._1;
    cacheApi.remove(k);
  }

  private Tuple<String, Session> getCacheKey(final Session session, final String key) {
    // Generate a unique id
    Session resultSession = session;
    Optional<String> uuid = session.get(SESSION_ID_KEY);
    if (!uuid.isPresent()) {
      uuid = Optional.of(UUID.randomUUID().toString());
      resultSession = session.adding(SESSION_ID_KEY, uuid.get());
    }
    final String id = uuid.get();
    return Tuple(id + "_" + key, resultSession);
  }

  @SuppressWarnings("unchecked")
  public <T> T getFromCache(final Session session, final String key) {
    assert session.get(key).isPresent();
    return (T) cacheApi.get(getCacheKey(session, key)._1).orElse(null);
  }

  private String getJumpUrl(final Http.Request request) {
    Optional<String> originalUrl = request.session().get(PlayAuthenticate.ORIGINAL_URL);
    return originalUrl.orElseGet(
        () -> getUrl(getResolver().afterAuth(), SETTING_KEY_AFTER_AUTH_FALLBACK));
  }

  private String getUrl(final Call c, final String settingFallback) {
    // this can be null if the user did not correctly define the
    // resolver
    if (c != null) {
      return c.url();
    } else {
      // go to root instead, but log this
      sLogger.warn("Resolver did not contain information about where to go - redirecting to /");
      final String afterAuthFallback;
      if (getConfiguration().hasPath(settingFallback)
          && !(afterAuthFallback = getConfiguration().getString(settingFallback)).isEmpty()) {
        return afterAuthFallback;
      }
      // Not even the config setting was there or valid...meh
      sLogger.error("Config setting '" + settingFallback + "' was not present!");
      return "/";
    }
  }

  public Result loginAndRedirect(final Http.Request request, final AuthUser loginUser) {
    Session session = request.session().removing(PlayAuthenticate.ORIGINAL_URL);
    return Controller.redirect(getJumpUrl(request)).withSession(storeUser(session, loginUser));
  }

  private AuthUser signupUser(
      final AuthUser u, final Http.Request request, final AuthProvider provider)
      throws AuthException {
    final Object id = getUserService().save(u, request);
    if (id == null) {
      throw new AuthException(
          messagesApi
              .preferred(preferredLangs)
              .at("playauthenticate.core.exception.signupuser_failed"));
    }
    provider.afterSave(u, id, request.session());
    return u;
  }

  public Result handleAuthentication(
      final String provider, final Http.Request request, final Object payload) {
    final AuthProvider ap = getProvider(provider);
    if (ap == null) {
      // Provider wasn't found and/or user was fooling with our stuff -
      // tell him off:
      return Controller.notFound(
          messagesApi
              .preferred(preferredLangs)
              .at("playauthenticate.core.exception.provider_not_found", provider));
    }
    try {
      final Object o = ap.authenticate(request, payload);
      if (o instanceof String) {
        return Controller.redirect((String) o);
      } else if (o instanceof Result) {
        return (Result) o;
      } else if (o instanceof AuthUser) {

        final AuthUser newUser = (AuthUser) o;
        final Session session = request.session();

        // We might want to do merging here:
        // Adapted from:
        // http://stackoverflow.com/questions/6666267/architecture-for-merging-multiple-user-accounts-together
        // 1. The account is linked to a local account and no session
        // cookie is present --> Login
        // 2. The account is linked to a local account and a session
        // cookie is present --> Merge
        // 3. The account is not linked to a local account and no
        // session cookie is present --> Signup
        // 4. The account is not linked to a local account and a session
        // cookie is present --> Linking Additional account

        // get the user with which we are logged in - is null if we
        // are
        // not logged in (does NOT check expiration)

        AuthUser oldUser = getUser(session);

        // checks if the user is logged in (also checks the expiration!)
        boolean isLoggedIn = isLoggedIn(session);

        Object oldIdentity = null;

        // check if local user still exists - it might have been
        // deactivated/deleted,
        // so this is a signup, not a link
        if (isLoggedIn) {
          oldIdentity = getUserService().getLocalIdentity(oldUser);
          isLoggedIn = oldIdentity != null;
          if (!isLoggedIn) {
            // if isLoggedIn is false here, then the local user has
            // been deleted/deactivated
            // so kill the session
            logout(session);
            oldUser = null;
          }
        }

        final Object loginIdentity = getUserService().getLocalIdentity(newUser);
        final boolean isLinked = loginIdentity != null;

        final AuthUser loginUser;
        if (isLinked && !isLoggedIn) {
          // 1. -> Login
          loginUser = newUser;

        } else if (isLinked) {
          // 2. -> Merge

          // merge the two identities and return the AuthUser we want
          // to use for the log in
          if (isAccountMergeEnabled() && !loginIdentity.equals(oldIdentity)) {
            throw new UnsupportedOperationException("account merge is disabled");
            // account merge is enabled
            // and
            // The currently logged in user and the one to log in
            // are not the same, so shall we merge?
          } else {
            // the currently logged in user and the new login belong
            // to the same local user,
            // or Account merge is disabled, so just change the log
            // in to the new user
            loginUser = newUser;
          }

        } else if (!isLoggedIn) {
          // 3. -> Signup
          loginUser = signupUser(newUser, request, ap);
        } else {
          // !isLinked && isLoggedIn:

          // 4. -> Link additional
          if (isAccountAutoLink()) {
            // Account auto linking is enabled

            loginUser = getUserService().link(oldUser, newUser);
          } else {
            throw new UnsupportedOperationException("link suggestion page is disabled");
          }
        }

        return loginAndRedirect(request, loginUser);
      } else {
        return Controller.internalServerError(
            messagesApi.preferred(preferredLangs).at("playauthenticate.core.exception.general"));
      }
    } catch (final AuthException e) {
      final Call c = getResolver().onException(e);
      if (c != null) {
        return Controller.redirect(c);
      } else {
        final String message = e.getMessage();
        if (message != null) {
          return Controller.internalServerError(message);
        } else {
          return Controller.internalServerError();
        }
      }
    }
  }

  public AuthProvider getProvider(final String providerKey) {
    return AuthProvider.Registry.get(providerKey);
  }
}
