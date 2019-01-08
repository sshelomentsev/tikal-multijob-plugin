package com.tikal.jenkins.plugins.multijob;

import hudson.model.*;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.util.List;

public class MultiJobResumeBuild implements RunAction2, StaplerProxy {

    private transient Run<?, ?> run;

    public MultiJobResumeBuild(Run<?, ?> run) {
        this.run = run;
    }

    public String getIconFileName() {
        return Jenkins.getInstance().hasPermission(Job.BUILD) ? "plugin/jenkins-multijob-plugin/tool32.png" : null;
	}

    public String getDisplayName() {
		return Messages.MultiJobResumeBuild_DisplayName();
	}

    public String getUrlName() {
		return "resume";
	}

    public String getInfo() {
		return "Resume build";
	}

    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException {
        // if rebuild plugin is available let it handle build resuming
        if (Utils.rebuildPluginAvailable()) {
            run.replaceAction(new WasResumedAction()); // add or replace existing one
            rsp.sendRedirect2("../rebuild");
            return;
        }

        final MultiJobResumeControl control = new MultiJobResumeControl(run);
        List<Action> actions = Utils.copyBuildCauses(run);
        actions.add(control);
        actions.add(new CauseAction(new ResumeCause(run)));
        Jenkins.getInstance().getQueue().schedule2((Queue.Task) run.getParent(), 0, actions);
        rsp.sendRedirect2(Jenkins.getInstance().getRootUrl() + run.getParent().getUrl());
    }

    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    private List<Action> copyBuildCauses() {
        List<Action> actions = new ArrayList<Action>();
        boolean hasUserIdCause = false;
        for (Object cause : run.getCauses()) {
            if (cause instanceof Cause.UserIdCause) {
                hasUserIdCause = true;
                actions.add(new CauseAction(new Cause.UserIdCause()));
            } else {
                actions.add(new CauseAction((Cause) cause));
            }
        }
        if (!hasUserIdCause) {
            actions.add(new CauseAction(new Cause.UserIdCause()));
        }
        actions.addAll(run.getActions(ParametersAction.class));
        return actions;
    }

    @Override
    public Object getTarget() {
        Jenkins.getInstance().checkPermission(Job.BUILD);
        return this;
    }
}
