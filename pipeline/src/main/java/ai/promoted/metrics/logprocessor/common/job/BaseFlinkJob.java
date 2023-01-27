package ai.promoted.metrics.logprocessor.common.job;

import ai.promoted.metrics.logprocessor.common.constant.Constants;
import ai.promoted.metrics.logprocessor.common.util.DebugIds;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.flink.api.dag.Transformation;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Option;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/** ABC for a Flink job. */
public abstract class BaseFlinkJob implements FlinkSegment, Callable<Integer> {
    private static final Logger LOGGER = LogManager.getLogger(BaseFlinkJob.class);

    @Option(names = {"--jobLabel"}, defaultValue = Constants.LIVE_LABEL,
            description = "Label for Flink job.  Defaults to 'live'")
    public String jobLabel = Constants.LIVE_LABEL;

    // This is just for raw log jobs.  We don't expect to set this above 1.
    // TODO(PRO-1674): remove the job specific arg names
    @Option(names = {"--parallelism", "--rawJobParallelism", "--joinJobParallelism"}, defaultValue = "1", description = "The parallelism value for this job.  Default=1")
    protected int parallelism = 1;

    @Option(names = {"--maxParallelism"}, required = true, description = "The maxParallelism value for this job.  See https://nightlies.apache.org/flink/flink-docs-release-1.13/docs/dev/execution/parallel/ for more details.  Required since this value is important and should not change between savepoints.")
    public int maxParallelism;

    @Option(names = {"--operatorParallelismMultiplier"}, description = "Map of operator uid to operator parallelism multipliers.  Takes the highest priority over other related flags.  Default=Empty (which means fall back to the other parallelism fields)")
    Map<String, Float> operatorParallelismMultiplier = ImmutableMap.of();

    @Option(names = {"--defaultSinkParallelismMultiplier"}, description = "If set, sets sink parallelism to this value multiplied by --parallelism.  This flag is has lower priority than --operatorParallelismMultiplier but is higher than --defaultSinkParallelism.  Default=null (no multiplier applied)")
    Float defaultSinkParallelismMultiplier = null;

    @Option(names = {"--defaultSinkParallelism"}, defaultValue = "1", description = "The default parallelism to use for S3 sinks.  Lowest priority flag.  Default=1")
    protected Integer defaultSinkParallelism = 1;

    @Option(names = {"--no-disableAutoGeneratedUIDs"}, negatable = true, description = "Whether to disableAutoGeneratedUIDs.")
    public boolean disableAutoGeneratedUIDs = true;

    @Option(names = {"--no-enableObjectReuse"}, negatable = true, description = "Whether to enableObjectReuse.")
    boolean enableObjectReuse = true;

    //////// CHECKPOINT OPTIONS

    // TODO - optimize the checkpointing interval.
    @Option(names = {"--checkpointInterval"}, defaultValue = "PT5M", description = "Checkpoint interval duration.  Default=PT5M.")
    public Duration checkpointInterval = Duration.ofMinutes(5);

    @Option(names = {"--minPauseBetweenCheckpoints"}, defaultValue = "5000", description = "minPauseBetweenCheckpoints in milliseconds.")
    int minPauseBetweenCheckpoints;

    // TODO - optimize the checkpointing timeout.
    @Option(names = {"--checkpointTimeoutDuration"}, defaultValue = "PT1H", description = "Checkpoint timeout in milliseconds.  Default=3600000")
    public Duration checkpointTimeout = Duration.ofHours(1);

    @Option(names = {"--no-unalignedCheckpoints"}, negatable = true, description = "Whether to use unaligned checkpoints.")
    boolean unalignedCheckpoints = true;

    @Option(names = {"--tolerableCheckpointFailureNumber"}, defaultValue = "3", description = "Tolerable checkpoint failures.  Default=3.  Arbitrary value.  We want to handle two random failures in a row before killing the job.")
    int tolerableCheckpointFailureNumber;

    @Option(names = {"--checkpointingMode"}, defaultValue = "EXACTLY_ONCE", description = "Checkpointing mode.")
    CheckpointingMode checkpointingMode = CheckpointingMode.EXACTLY_ONCE;

    @Option(names = {"--externalizedCheckpointCleanup"}, defaultValue = "RETAIN_ON_CANCELLATION", description = "Default to RETAIN_ON_CANCELLATION.")
    CheckpointConfig.ExternalizedCheckpointCleanup externalizedCheckpointCleanup = CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION;

    //////// DEBUG OPTIONS

    @Option(names = {"--debugUserIds"}, description = "Set of userIds substrings to log more join debug logs. Acts as an OR with other debug fields. Used to track down missing flattened events.")
    Set<String> debugUserIds = ImmutableSet.of();

    @Option(names = {"--debugLogUserIds"}, description = "Set of logUserIds substrings to log more join debug logs. Acts as an OR with other debug fields. Used to track down missing flattened events.")
    Set<String> debugLogUserIds = ImmutableSet.of();

    @Option(names = {"--debugSessionIds"}, description = "Set of sessionIds substrings to log more join debug logs. Acts as an OR with other debug fields. Used to track down missing flattened events.")
    Set<String> debugSessionIds = ImmutableSet.of();

    @Option(names = {"--debugViewIds"}, description = "Set of viewIds substrings to log more join debug logs. Acts as an OR with other debug fields. Used to track down missing flattened events.")
    Set<String> debugViewIds = ImmutableSet.of();

    @Option(names = {"--debugAutoViewIds"}, description = "Set of autoViewIds substrings to log more join debug logs. Acts as an OR with other debug fields. Used to track down missing flattened events.")
    Set<String> debugAutoViewIds = ImmutableSet.of();

    @Option(names = {"--debugRequestIds"}, description = "Set of requestIds substrings to log more join debug logs. Acts as an OR with other debug fields. Used to track down missing flattened events.")
    Set<String> debugRequestIds = ImmutableSet.of();

    @Option(names = {"--debugInsertionIds"}, description = "Set of insertionIds substrings to log more join debug logs. Acts as an OR with other debug fields. Used to track down missing flattened events.")
    Set<String> debugInsertionIds = ImmutableSet.of();

    @Option(names = {"--debugImpressionIds"}, description = "Set of impressionIds substrings to log more join debug logs. Acts as an OR with other debug fields. Used to track down missing flattened events.")
    Set<String> debugImpressionIds = ImmutableSet.of();

    @Option(names = {"--debugActionIds"}, description = "Set of actionIds substrings to log more join debug logs. Acts as an OR with other debug fields. Used to track down missing flattened events.")
    Set<String> debugActionIds = ImmutableSet.of();

    /** Returns if this job is the live/prod version of a job. */
    public boolean isLiveLabel() {
        return "".equals(jobLabel) || Constants.LIVE_LABEL.equals(jobLabel);
    }

    /** Returns the name of the Flink job. */
    protected abstract String getJobName();

    /** Returns the job label for this job.  If live/prod, it will return an empty string. */
    public String getJobLabel() {
        return isLiveLabel() ? "" : jobLabel;
    }

    public String prefixJobLabel(String input) {
        return (isLiveLabel() ? "" : jobLabel + ".") + input;
    }

    /** Returns the Kafka consumer group id for this job. */
    // This is located here due to the scope of the jobLabel argument.
    public String toKafkaConsumerGroupId(String baseKafkaGroupId) {
        return prefixJobLabel(baseKafkaGroupId);
    }

    /** Main for running the Flink job.  Will throw a RTE until overridden. */
    public static void main(String[] args) {
        /* Usually, this is implemented as:
        int exitCode = new CommandLine(new MyJob()).execute(args);
        System.exit(exitCode);
        */
        throw new UnsupportedOperationException("Statically define main in your implementing job class.");
    }

    protected <T> SingleOutputStreamOperator<T> add(SingleOutputStreamOperator<T> operator, String uid) {
        operator = operator.uid(uid).name(uid);
        Optional<Integer> operatorParallelism = getOperatorParallelism(uid);
        if (operatorParallelism.isPresent()) {
            LOGGER.info("operator {} parallelism={}", uid, operatorParallelism.get());
            operator = operator.setParallelism(operatorParallelism.get());
        }
        return operator;
    }

    protected <T> SingleOutputStreamOperator<T> add(DataStreamSource<T> source, String uid) {
        SingleOutputStreamOperator<T> out = source.uid(uid).name(uid);
        Optional<Integer> operatorParallelism = getOperatorParallelism(uid);
        if (operatorParallelism.isPresent()) {
            LOGGER.info("source {} parallelism={}", uid, operatorParallelism.get());
            out = out.setParallelism(operatorParallelism.get());
        }
        return out;
    }

    protected <T> DataStreamSink<T> add(DataStreamSink<T> sink, String uid) {
        sink = sink.uid(uid).name(uid);
        int sinkParallelism = getSinkParallelism(uid);
        LOGGER.info("sink {} parallelism={}", uid, sinkParallelism);
        return sink.setParallelism(sinkParallelism);
    }

    @VisibleForTesting
    Optional<Integer> getOperatorParallelism(String uid) {
        Float multiplier = operatorParallelismMultiplier.get(uid);
        if (multiplier != null) {
            return Optional.of(rangeParallelism(Math.round(multiplier * parallelism)));
        }
        return Optional.empty();
    }

    @VisibleForTesting
    int getSinkParallelism(String uid) {
        Optional<Integer> p = getOperatorParallelism(uid);
        if (p.isPresent()) {
            return p.get();
        } else if (defaultSinkParallelismMultiplier != null) {
            return rangeParallelism(Math.round(defaultSinkParallelismMultiplier * parallelism));
        } else {
            return rangeParallelism(defaultSinkParallelism);
        }
    }

    private int rangeParallelism(int p) {
        return Math.max(1, Math.min(maxParallelism, p));
    }

    // Build a list of sink transformations for our tests.
    // All sinks should be in sinkTransformations.
    public ArrayList<Transformation<?>> sinkTransformations = new ArrayList<>();

    protected void addSinkTransformation(@Nullable Transformation transformation) {
        addSinkTransformation(Optional.ofNullable(transformation));
    }

    protected void addSinkTransformation(Optional<Transformation> transformation) {
        transformation.ifPresent(sinkTransformations::add);
    }

    protected void addSinkTransformations(Iterable<Transformation> transformations) {
        transformations.forEach(sinkTransformations::add);
    }

    // where it is used (i.e. DebugIds debugIds = getDebugIds()).
    private transient DebugIds cachedDebugIds;

    /** Returns DebugIds from command line arguments.
     * Due to serialization with flink, it's best to instantiate a reference local to the function
     * where it's used.
     */
    protected DebugIds getDebugIds() {
        if (cachedDebugIds == null) {
            cachedDebugIds = DebugIds.builder()
                    .setUserIds(debugUserIds)
                    .setLogUserIds(debugLogUserIds)
                    .setSessionIds(debugSessionIds)
                    .setViewIds(debugViewIds)
                    .setAutoViewIds(debugAutoViewIds)
                    .setRequestIds(debugRequestIds)
                    .setInsertionIds(debugInsertionIds)
                    .setImpressionIds(debugImpressionIds)
                    .setActionIds(debugActionIds)
                    .build();
        }
        return cachedDebugIds;
    }

    /** Configures a StreamExecutionEnvironment.
     * @param env the environment to configure
     * @param parallelism the parallelism for the job
     * @param maxParallelism the maxParallelism for the job
     */
    public void configureExecutionEnvironment(StreamExecutionEnvironment env, int parallelism, int maxParallelism) {
        getProtoClasses().forEach(c -> FlinkSegment.optionalRegisterProtobufSerializer(env.getConfig(), c));

        if (disableAutoGeneratedUIDs) {
            env.getConfig().disableAutoGeneratedUIDs();
        }

        env.setParallelism(parallelism);
        if (maxParallelism > 0) {
            env.setMaxParallelism(maxParallelism);
        }

        if (enableObjectReuse) {
            env.getConfig().enableObjectReuse();
        }

        if (checkpointInterval.toMillis() > 0) {
            env.enableCheckpointing(checkpointInterval.toMillis());
        }
        env.getCheckpointConfig().setCheckpointingMode(checkpointingMode);
        // TODO - evaluate if we want setMinPauseBetweenCheckpoints.
        if (minPauseBetweenCheckpoints > 0) {
            env.getCheckpointConfig().setMinPauseBetweenCheckpoints(minPauseBetweenCheckpoints);
        }
        if (unalignedCheckpoints) {
            env.getCheckpointConfig().enableUnalignedCheckpoints();
        }
        if (checkpointTimeout.toMillis() > 0) {
            env.getCheckpointConfig().setCheckpointTimeout(checkpointTimeout.toMillis());
        }
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(tolerableCheckpointFailureNumber);
        env.getCheckpointConfig().enableExternalizedCheckpoints(externalizedCheckpointCleanup);
    }
}
