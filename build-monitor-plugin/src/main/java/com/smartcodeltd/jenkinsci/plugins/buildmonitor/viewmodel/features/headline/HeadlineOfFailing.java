package com.smartcodeltd.jenkinsci.plugins.buildmonitor.viewmodel.features.headline;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.smartcodeltd.jenkinsci.plugins.buildmonitor.readability.Lister;
import com.smartcodeltd.jenkinsci.plugins.buildmonitor.readability.Pluraliser;
import com.smartcodeltd.jenkinsci.plugins.buildmonitor.viewmodel.BuildViewModel;
import com.smartcodeltd.jenkinsci.plugins.buildmonitor.viewmodel.JobView;
import groovy.json.StringEscapeUtils;
import hudson.tasks.test.AbstractTestResultAction;
import jenkins.model.InterruptedBuildAction;

import java.util.List;
import java.util.Set;

import static com.google.common.collect.Iterables.contains;
import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newLinkedList;
import static hudson.model.Result.FAILURE;
import static hudson.model.Result.SUCCESS;
import static hudson.model.Result.UNSTABLE;

public class HeadlineOfFailing implements CandidateHeadline {

    private final JobView job;
    private final HeadlineConfig config;

    public HeadlineOfFailing(JobView job, HeadlineConfig config) {
        this.job = job;
        this.config = config;
    }

    @Override
    public boolean isApplicableTo(JobView job) {
        return contains(newArrayList(FAILURE, UNSTABLE), job.lastCompletedBuild().result());
    }

    @Override
    public Headline asJson() {
        return new Headline(text(job.lastCompletedBuild()));
    }

    private String text(BuildViewModel lastBuild) {
        List<BuildViewModel> failedBuildsNewestToOldest = failedBuildsSince(lastBuild);
        Optional<AbstractTestResultAction> abstractTestResultAction = lastBuild.detailsOf(AbstractTestResultAction.class);
        String failingTestsString = "";
        if( abstractTestResultAction.isPresent()) {
            AbstractTestResultAction testResultAction = abstractTestResultAction.get();
            failingTestsString = testResultAction.getFailCount() > 0 && testResultAction.getTotalCount() > 0 ?
                    String.format("%d of %d tests failing \n", testResultAction.getFailCount(), testResultAction.getTotalCount()) : "";

        }
        String buildsFailedSoFar = failingTestsString + Pluraliser.pluralise(
                "%s build has failed",
                "%s builds have failed",
                failedBuildsNewestToOldest.size()
        );

        BuildViewModel firstFailedBuild = failedBuildsNewestToOldest.isEmpty()
                ? lastBuild
                : getLast(failedBuildsNewestToOldest);



        return Lister.describe(
                buildsFailedSoFar,
                buildsFailedSoFar + " since %s committed their changes",
                newLinkedList(responsibleFor(firstFailedBuild))
        );
    }

    private List<BuildViewModel> failedBuildsSince(BuildViewModel build) {
        BuildViewModel currentBuild = build;

        List<BuildViewModel> failedBuilds = Lists.newArrayList();

        while (! SUCCESS.equals(currentBuild.result())) {

            if (! currentBuild.isRunning()) {
                failedBuilds.add(currentBuild);
            }

            if (! currentBuild.hasPreviousBuild()) {
                break;
            }

            currentBuild = currentBuild.previousBuild();
        }

        return failedBuilds;
    }

    private Set<String> responsibleFor(BuildViewModel build) {
        return config.displayCommitters
                ? build.culprits()
                : Sets.<String>newHashSet();
    }
}
