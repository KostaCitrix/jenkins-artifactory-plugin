package org.jfrog.hudson.release.scm;

import java.io.IOException;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.TaskListener;

public class JenkinsBuildImpl<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> implements JenkinsBuild {

    private final AbstractBuild<P, R> build;

    public JenkinsBuildImpl(AbstractBuild<P, R> build) {
        this.build = build;
    }

    public EnvVars getEnvironment(TaskListener log) throws IOException, InterruptedException {
        return build.getEnvironment(log);
    }

    public Result getResult() {
        return build.getResult();
    }

    public AbstractBuild<P, R> getBuild() {
        return build;
    }
}
