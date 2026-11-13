package nu.fgv.spexipedia.server.user;

/**
 * Client roles on {@code fgv-spexipedia}. {@code AUTHOR}, {@code EDITOR} and {@code ADMIN} are granted in addition to
 * {@code USER}, which every approved FGV user has. {@code SYNC} is held by the service accounts of the applications
 * that keep a copy of base data, {@code REVIVAL} by Nyuppsättning's.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public enum Role {
    USER,
    AUTHOR,
    EDITOR,
    ADMIN,
    SYNC,
    REVIVAL
}
