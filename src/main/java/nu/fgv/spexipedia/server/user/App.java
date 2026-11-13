package nu.fgv.spexipedia.server.user;

/**
 * The other FGV applications Spexipedia links to, by their Keycloak client. A user has access to one when their token
 * carries its {@code USER} role (the web clients carry each other's {@code USER} roles).
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public enum App {
    SPEXREGISTER("spexregister"),
    SPEXGRAM("fgv-spexgram"),
    SPEXIFY("fgv-spexify"),
    SPEXFLIX("fgv-spexflix"),
    ARCHIVE("fgv-archive"),
    REVIVAL("fgv-revival");

    private final String clientId;

    App(final String clientId) {
        this.clientId = clientId;
    }

    public String clientId() {
        return clientId;
    }
}
