package com.vulncheck;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.supplier.RepositorySystemSupplier;

import java.nio.file.Path;

public final class MavenResolverFactory {

    private MavenResolverFactory() {
    }

    public static RepositorySystem createRepositorySystem() {
        return new RepositorySystemSupplier().get();
    }

    public static RepositorySystemSession createSession(
            RepositorySystem repositorySystem,
            Path localRepositoryPath
    ) {
        return createSession(repositorySystem, localRepositoryPath, null);
    }

    /**
     * Creates a session that mirrors ALL remote repository requests through
     * the given Nexus URL. This prevents Aether's internal artifact descriptor
     * reader from reaching out to Maven Central directly — important in
     * corporate networks that block direct internet access.
     */
    public static RepositorySystemSession createSession(
            RepositorySystem repositorySystem,
            Path localRepositoryPath,
            NexusCredentials nexusCredentials
    ) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_IGNORE);

        LocalRepository localRepository =
                new LocalRepository(localRepositoryPath);

        session.setLocalRepositoryManager(
                repositorySystem.newLocalRepositoryManager(
                        session,
                        localRepository
                )
        );
        session.setOffline(false);

        // Mirror all repositories through the corporate Nexus so that the
        // internal DefaultArtifactDescriptorReader does NOT try to reach
        // repo.maven.apache.org (which is typically blocked in corporate env).
        if (nexusCredentials != null) {
            session.setMirrorSelector(new MirrorSelector() {
                @Override
                public RemoteRepository getMirror(RemoteRepository repository) {
                    // Don't mirror our own Nexus repo to avoid infinite loop
                    if ("nexus".equals(repository.getId())) {
                        return null;
                    }
                    return new RemoteRepository.Builder(
                            "nexus-mirror-" + repository.getId(),
                            repository.getContentType(),
                            nexusCredentials.url()
                    )
                            .setAuthentication(nexusCredentials.toAuthentication())
                            .build();
                }
            });
        }

        return session;
    }
}
