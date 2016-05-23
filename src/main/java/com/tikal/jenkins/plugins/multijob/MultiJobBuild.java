package com.tikal.jenkins.plugins.multijob;

import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

@ExportedBean(defaultVisibility = 999)
public class MultiJobBuild extends Build<MultiJobProject, MultiJobBuild> {

    private List<SubBuild> subBuilds;
    private MultiJobChangeLogSet changeSets = new MultiJobChangeLogSet(this);
    private Map<String, SubBuild> subBuildsMap = new HashMap<String, SubBuild>();

    private static final Logger LOGGER = Logger.getLogger(MultiJobBuild.class.getName());

    public MultiJobBuild(MultiJobProject project) throws IOException {
        super(project);
    }

    @Override
    public ChangeLogSet<? extends Entry> getChangeSet() {
        return super.getChangeSet();
    }

    public void addChangeLogSet(ChangeLogSet<? extends Entry> changeLogSet) {
        if (changeLogSet != null) {
            this.changeSets.addChangeLogSet(changeLogSet);
        }
    }

    public MultiJobBuild(MultiJobProject project, File buildDir)
            throws IOException {
        super(project, buildDir);
    }

    @Override
    public synchronized void doStop(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException {
        super.doStop(req, rsp);
    }

    @Override
    public void addAction(Action a) {
        super.addAction(a);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        execute(new MultiJobRunnerImpl());
    }

    public List<SubBuild> getBuilders() {
        MultiJobBuild multiJobBuild = getParent().getNearestBuild(getNumber());
        return multiJobBuild.getSubBuilds();
    }

    public String getBuildParams(SubBuild subBuild) {
        try {
            AbstractProject project = (AbstractProject) Jenkins.getInstance()
            		.getItem(subBuild.getJobName(), this.getParent(), AbstractProject.class);;
            Run build = project.getBuildByNumber(subBuild.getBuildNumber());
            ParametersAction action = build.getAction(ParametersAction.class);
            List<ParameterValue> parameters = action.getParameters();
            StringBuffer buffer = new StringBuffer();
            for (ParameterValue parameterValue : parameters) {
                StringParameterValue stringParameter;
                try {
                    stringParameter = ((StringParameterValue) parameterValue);
                } catch (Exception e) {
                    continue;
                }
                String value = stringParameter.value;
                String name = stringParameter.getName();
                buffer.append("<input type='text' size='15' value='")
                        .append(name)
                        .append("' readonly/>")
                        .append("&nbsp;")
                        .append("<input type='text' size='35' value='")
                        .append(value)
                        .append("'/ readonly>")
                        .append("</br>");
            }
            return buffer.toString();
        } catch (Exception e) {
            return "Failed to retrieve build parameters.";
        }
    }

    private void updateLastMetrics(SubBuild subBuild) {
        for (SubBuild sub : getPreviousBuild().getSubBuilds()) {
            boolean is = false;
            if (sub.getJobName().equals(subBuild.getJobName())) {
                if (null != sub.getPhaseName() && null != subBuild.getPhaseName()) {
                    is = sub.getPhaseName().equals(subBuild.getPhaseName());
                } else {
                    is = true;
                }
            }
            if (is) {
                Long successTimestamp = sub.getSuccessTimestamp();
                Long failureTimestamp = sub.getFailureTimestamp();
                Result result = subBuild.getResult();
                if (null != result) {
                    if (Result.SUCCESS.equals(result)) {
                        successTimestamp = subBuild.getBuild().getTimeInMillis();
                    }
                    if (Result.FAILURE.equals(result)) {
                        failureTimestamp = subBuild.getBuild().getTimeInMillis();
                    }
                }
                subBuild.setSuccessTimestamp(successTimestamp);
                subBuild.setFailureTimestamp(failureTimestamp);
            }
        }
    }

    public void addSubBuild(SubBuild subBuild) {
        updateLastMetrics(subBuild);
        String key = subBuild.getPhaseName().concat(subBuild.getJobName())
                .concat(String.valueOf(subBuild.getBuildNumber()));
        if (subBuildsMap.containsKey(key)) {
            SubBuild e = subBuildsMap.get(key);
            Collections.replaceAll(getSubBuilds(), e, subBuild);
        } else {
            getSubBuilds().add(subBuild);
        }
        subBuildsMap.put(key, subBuild);

        if (project.isSurviveRestart() && isBuilding()) {
            String path = project.getConfigFile().getFile().getParent() + "/com.tikal.jenkins.plugins.multijob" +
                    ".resume" + String.valueOf(getNumber()) + ".xml";
            XmlFile resumeConfigFile = new XmlFile(new File(path));
            try {
                resumeConfigFile.write(this);
            } catch (IOException e) {
                LOGGER.severe("Failed to save build state to resume config for " + getProject().getDisplayName() +
                                      " #" + getNumber());
            }
        }
    }

    @Exported
    public List<SubBuild> getSubBuilds() {
        if (subBuilds == null)
            subBuilds = new CopyOnWriteArrayList<SubBuild>();
        return subBuilds;
    }

    protected class MultiJobRunnerImpl extends
            Build<MultiJobProject, MultiJobBuild>.BuildExecution {
        @Override
        public Result run(BuildListener listener) throws Exception {
            super.run(listener);
            String path = getProject().getRootDir().getAbsolutePath() + "/com.tikal.jenkins.plugins.multijob.resume" +
                    String.valueOf(getNumber()) + ".xml";
            File configFile = new File(path);
            if (configFile.exists()) {
                try {
                    Files.delete(configFile.toPath());
                } catch (IOException e) {
                    listener.getLogger().println("[MultiJobPlugin] Failed to delete resume build config");
                }
            }
            return computeResult();
        }

        private Result computeResult() {
            MultiJobResumeBuild action = new MultiJobResumeBuild(super.getBuild());
            if (isAborted()) {
                super.getBuild().addAction(action);
                return Result.ABORTED;
            }
            if (isFailure()) {
                super.getBuild().addAction(action);
                return Result.FAILURE;
            }
            if (isUnstable()) {
                return Result.UNSTABLE;
            }
            return Result.SUCCESS;
        }

        private boolean isAborted() {
            return evaluateResult(Result.FAILURE);
        }

        private boolean isFailure() {
            return evaluateResult(Result.UNSTABLE);
        }

        private boolean isUnstable() {
            return evaluateResult(Result.SUCCESS);
        }

        private boolean evaluateResult(Result result) {
            List<SubBuild> builders = getBuilders();
            for (SubBuild subBuild : builders) {
                if (!subBuild.isRetry() && !subBuild.isAbort()) {
                    Result buildResult = subBuild.getResult();
                    if (buildResult != null && buildResult.isWorseThan(result)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @ExportedBean(defaultVisibility = 999)
    public static class SubBuild {

        private final String parentJobName;
        private final int parentBuildNumber;
        private final String jobName;
        private final int buildNumber;
        private final String phaseName;
        private final Result result;
        private final String icon;
        private final String duration;
        private final String url;
        private final boolean retry;
        private final boolean aborted;
        private final AbstractBuild<?, ?> build;

        private Long successTimestamp = null;
        private Long failureTimestamp = null;

        public SubBuild(String parentJobName, int parentBuildNumber,
                String jobName, int buildNumber, String phaseName,
                Result result, String icon, String duration, String url,
                AbstractBuild<?, ?> build) {
            this.parentJobName = parentJobName;
            this.parentBuildNumber = parentBuildNumber;
            this.jobName = jobName;
            this.buildNumber = buildNumber;
            this.phaseName = phaseName;
            this.result = result;
            this.icon = icon;
            this.duration = duration;
            this.url = url;
            this.retry = false;
            this.aborted = false;
            this.build = build;
        }

        public SubBuild(String parentJobName, int parentBuildNumber,
                String jobName, int buildNumber, String phaseName,
                Result result, String icon, String duration, String url,
                boolean retry, boolean aborted, AbstractBuild<?, ?> build) {
            this.parentJobName = parentJobName;
            this.parentBuildNumber = parentBuildNumber;
            this.jobName = jobName;
            this.buildNumber = buildNumber;
            this.phaseName = phaseName;
            this.result = result;
            this.icon = icon;
            this.duration = duration;
            this.url = url;
            this.retry = retry;
            this.aborted = aborted;
            this.build = build;
        }

        public Long getSuccessTimestamp() {
            return successTimestamp;
        }

        public void setSuccessTimestamp(Long successTimestamp) {
            this.successTimestamp = successTimestamp;
        }

        public Long getFailureTimestamp() {
            return failureTimestamp;
        }

        public void setFailureTimestamp(Long failureTimestamp) {
            this.failureTimestamp = failureTimestamp;
        }

        @Exported
        public String getDuration() {
            return duration;
        }

        @Exported
        public boolean isRetry() {
            return retry;
        }


        @Exported
        public boolean isAbort() {
            return aborted;
        }

        @Exported
        public String getIcon() {
            return icon;
        }

        @Exported
        public String getUrl() {
            return url;
        }

        @Exported
        public String getPhaseName() {
            return phaseName;
        }

        @Exported
        public String getParentJobName() {
            return parentJobName;
        }

        @Exported
        public int getParentBuildNumber() {
            return parentBuildNumber;
        }

        @Exported
        public String getJobName() {
            return jobName;
        }

        @Exported
        public int getBuildNumber() {
            return buildNumber;
        }

        @Exported
        public Result getResult() {
            return result;
        }

        @Override
        public String toString() {
            return "SubBuild [parentJobName=" + parentJobName
                    + ", parentBuildNumber=" + parentBuildNumber + ", jobName="
                    + jobName + ", buildNumber=" + buildNumber + "]";
        }

		@Exported
		public AbstractBuild<?,?> getBuild() {
			return build;
		}
    }
}
