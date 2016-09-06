package org.jfrog.hudson.release.scm;

import java.io.IOException;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.TaskListener;

/**
 * @see org.jfrog.hudson.release.scm.BuildWrapper
 */
public class BuildWrapperImpl<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> implements BuildWrapper {

    private final AbstractBuild<P, R> build;

    public BuildWrapperImpl(AbstractBuild<P, R> build) {
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
