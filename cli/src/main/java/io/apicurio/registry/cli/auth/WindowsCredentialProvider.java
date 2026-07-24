package io.apicurio.registry.cli.auth;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.jboss.logging.Logger;

/**
 * Credential provider backed by the Windows Credential Manager, using the generic
 * credential API of advapi32 (CredWriteW, CredReadW, CredDeleteW).
 *
 * Credentials are stored as generic credentials under the target name
 * {@code <serviceName>/<account>}, so they are visible in the Credential Manager
 * control panel and are scoped to the current Windows user.
 */
class WindowsCredentialProvider implements CredentialProvider {

    private static final Logger log = Logger.getLogger(WindowsCredentialProvider.class);

    private static final int CRED_TYPE_GENERIC = 1;
    private static final int CRED_PERSIST_LOCAL_MACHINE = 2;
    private static final int ERROR_NOT_FOUND = 1168;

    /** CRED_MAX_CREDENTIAL_BLOB_SIZE — the largest secret the Credential Manager accepts. */
    private static final int MAX_BLOB_SIZE = 2560;

    private static Advapi32 library;

    private final String serviceName;

    WindowsCredentialProvider(final String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * Loads advapi32 on first use. The library must not be loaded from a static initializer:
     * the native image generator initializes application classes at build time, and the loaded
     * library is not something that can be carried in the image heap. Holding it in a field that
     * starts out null keeps the load at run time, on Windows only.
     */
    private static synchronized Advapi32 library() {
        if (library == null) {
            library = Native.load("Advapi32", Advapi32.class);
        }
        return library;
    }

    @Override
    public void store(final String account, final String secret) {
        final byte[] blob = secret.getBytes(StandardCharsets.UTF_16LE);
        if (blob.length > MAX_BLOB_SIZE) {
            throw new CredentialStoreException(
                    "Secret is too large for the Windows Credential Manager (the limit is "
                            + MAX_BLOB_SIZE + " bytes).");
        }
        // Memory of size 0 is not allowed, and an empty secret needs no bytes written.
        final Memory blobMemory = new Memory(Math.max(blob.length, 1));
        try {
            if (blob.length > 0) {
                blobMemory.write(0, blob, 0, blob.length);
            }
            final Credential credential = new Credential();
            credential.Type = CRED_TYPE_GENERIC;
            credential.TargetName = new WString(targetName(account));
            credential.UserName = new WString(account);
            credential.CredentialBlobSize = blob.length;
            credential.CredentialBlob = blobMemory;
            credential.Persist = CRED_PERSIST_LOCAL_MACHINE;
            if (!library().CredWriteW(credential, 0)) {
                throw new CredentialStoreException(
                        "CredWrite failed (error " + Native.getLastError() + ").");
            }
        } finally {
            // Do not leave the secret in native or heap memory once it has been handed to Windows.
            blobMemory.clear();
            blobMemory.close();
            Arrays.fill(blob, (byte) 0);
        }
    }

    @Override
    public String retrieve(final String account) {
        final PointerByReference credentialPointer = new PointerByReference();
        if (!library().CredReadW(new WString(targetName(account)), CRED_TYPE_GENERIC, 0,
                credentialPointer)) {
            final int error = Native.getLastError();
            if (error == ERROR_NOT_FOUND) {
                return null;
            }
            throw new CredentialStoreException("CredRead failed (error " + error + ").");
        }
        final Pointer pointer = credentialPointer.getValue();
        if (pointer == null) {
            // CredRead reported success without producing a credential; treat it as not found.
            return null;
        }
        byte[] blob = null;
        try {
            final Credential credential = new Credential(pointer);
            if (credential.CredentialBlob == null || credential.CredentialBlobSize <= 0) {
                return null;
            }
            blob = credential.CredentialBlob.getByteArray(0, credential.CredentialBlobSize);
            return new String(blob, StandardCharsets.UTF_16LE);
        } finally {
            if (blob != null) {
                Arrays.fill(blob, (byte) 0);
            }
            library().CredFree(pointer);
        }
    }

    @Override
    public void delete(final String account) {
        if (!library().CredDeleteW(new WString(targetName(account)), CRED_TYPE_GENERIC, 0)) {
            final int error = Native.getLastError();
            if (error == ERROR_NOT_FOUND) {
                log.debugf("No existing Credential Manager entry to delete.");
                return;
            }
            throw new CredentialStoreException("CredDelete failed (error " + error + ").");
        }
    }

    private String targetName(final String account) {
        return serviceName + "/" + account;
    }

    /**
     * Minimal mapping of the advapi32 credential functions. jna-platform does not provide
     * these bindings, so only the functions the CLI needs are declared here.
     */
    interface Advapi32 extends StdCallLibrary {

        boolean CredWriteW(Credential credential, int flags);

        boolean CredReadW(WString targetName, int type, int flags, PointerByReference credential);

        boolean CredDeleteW(WString targetName, int type, int flags);

        void CredFree(Pointer credential);
    }

    /**
     * The Win32 CREDENTIALW structure. Field names and order must match the native layout,
     * and JNA requires the fields to be public.
     */
    @Structure.FieldOrder({ "Flags", "Type", "TargetName", "Comment", "LastWritten",
            "CredentialBlobSize", "CredentialBlob", "Persist", "AttributeCount", "Attributes",
            "TargetAlias", "UserName" })
    public static class Credential extends Structure {

        public int Flags;
        public int Type;
        public WString TargetName;
        public WString Comment;
        public FileTime LastWritten;
        public int CredentialBlobSize;
        public Pointer CredentialBlob;
        public int Persist;
        public int AttributeCount;
        public Pointer Attributes;
        public WString TargetAlias;
        public WString UserName;

        public Credential() {
        }

        Credential(final Pointer pointer) {
            super(pointer);
            read();
        }
    }

    /**
     * The Win32 FILETIME structure, embedded by value in {@link Credential}. The CLI never
     * reads it, but it must be present so the surrounding fields are laid out correctly.
     */
    @Structure.FieldOrder({ "dwLowDateTime", "dwHighDateTime" })
    public static class FileTime extends Structure {

        public int dwLowDateTime;
        public int dwHighDateTime;
    }
}
