package software.amazon.iot.billinggroup;

import org.apache.commons.lang3.exception.ExceptionUtils;
import software.amazon.awssdk.services.iot.model.BillingGroupProperties;
import software.amazon.awssdk.services.iot.model.ConflictingResourceUpdateException;
import software.amazon.awssdk.services.iot.model.CreateBillingGroupRequest;
import software.amazon.awssdk.services.iot.model.DeleteBillingGroupRequest;
import software.amazon.awssdk.services.iot.model.DeleteConflictException;
import software.amazon.awssdk.services.iot.model.DescribeBillingGroupRequest;
import software.amazon.awssdk.services.iot.model.InternalException;
import software.amazon.awssdk.services.iot.model.InternalFailureException;
import software.amazon.awssdk.services.iot.model.InvalidRequestException;
import software.amazon.awssdk.services.iot.model.IotException;
import software.amazon.awssdk.services.iot.model.LimitExceededException;
import software.amazon.awssdk.services.iot.model.ListBillingGroupsRequest;
import software.amazon.awssdk.services.iot.model.ListBillingGroupsResponse;
import software.amazon.awssdk.services.iot.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.iot.model.ResourceAlreadyExistsException;
import software.amazon.awssdk.services.iot.model.ResourceNotFoundException;
import software.amazon.awssdk.services.iot.model.ServiceUnavailableException;
import software.amazon.awssdk.services.iot.model.Tag;
import software.amazon.awssdk.services.iot.model.TagResourceRequest;
import software.amazon.awssdk.services.iot.model.ThrottlingException;
import software.amazon.awssdk.services.iot.model.UnauthorizedException;
import software.amazon.awssdk.services.iot.model.UntagResourceRequest;
import software.amazon.awssdk.services.iot.model.UpdateBillingGroupRequest;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Centralized placeholder for:
 * api request construction
 * object translation to/from aws sdk
 * resource model construction for read/list handlers
 * mapping exceptions to appropriate Cloudformation exceptions
 */
public class Translator {

    static ProgressEvent<ResourceModel, CallbackContext> translateExceptionToProgressEvent(
            ResourceModel model, Exception e, Logger logger) {

        HandlerErrorCode errorCode = translateExceptionToErrorCode(e, logger);
        ProgressEvent<ResourceModel, CallbackContext> progressEvent =
                ProgressEvent.<ResourceModel, CallbackContext>builder()
                        .resourceModel(model)
                        .status(OperationStatus.FAILED)
                        .errorCode(errorCode)
                        .build();
        if (errorCode != HandlerErrorCode.InternalFailure) {
            progressEvent.setMessage(e.getMessage());
        }
        return progressEvent;
    }


    static HandlerErrorCode translateExceptionToErrorCode(Exception e, Logger logger) {
        logger.log(String.format("Translating exception \"%s\", stack trace: %s",
                e.getMessage(), ExceptionUtils.getStackTrace(e)));

        // We're handling all the exceptions documented in API docs
        // https://docs.aws.amazon.com/iot/latest/apireference/API_CreateBillingGroup.html#API_CreateBillingGroup_Errors
        // (+same pages for other APIs)
        // For Throttling and InternalFailure, we want CloudFormation to retry, and it will do so based on the error code.
        // Reference with Retryable/Terminal in comments for each: https://tinyurl.com/y378qdno
        if (e instanceof ResourceAlreadyExistsException) {
            return HandlerErrorCode.AlreadyExists;
        } else if (e instanceof software.amazon.cloudformation.exceptions.ResourceAlreadyExistsException) {
            return HandlerErrorCode.AlreadyExists;
        } else if (e instanceof InvalidRequestException) {
            return HandlerErrorCode.InvalidRequest;
        } else if (e instanceof LimitExceededException) {
            return HandlerErrorCode.ServiceLimitExceeded;
        } else if (e instanceof UnauthorizedException) {
            return HandlerErrorCode.AccessDenied;
        } else if (e instanceof InternalFailureException) {
            return HandlerErrorCode.ServiceInternalError;
        } else if (e instanceof InternalException) {
            return HandlerErrorCode.ServiceInternalError;
        } else if (e instanceof ServiceUnavailableException) {
            return HandlerErrorCode.ServiceInternalError;
        } else if (e instanceof ThrottlingException) {
            return HandlerErrorCode.Throttling;
        } else if (e instanceof ResourceNotFoundException) {
            return HandlerErrorCode.NotFound;
        } else if (e instanceof ConflictingResourceUpdateException | e instanceof DeleteConflictException) {
            return HandlerErrorCode.ResourceConflict;
        } else if (e instanceof IotException && ((IotException) e).statusCode() == 403) {
            return HandlerErrorCode.AccessDenied;
        } else {
            logger.log(String.format("Unexpected exception \"%s\", stack trace: %s",
                    e.getMessage(), ExceptionUtils.getStackTrace(e)));
            // Any other exception at this point is unexpected.
            return HandlerErrorCode.InternalFailure;
        }
    }

    static CreateBillingGroupRequest translateToCreateRequest(final ResourceModel model, final Map<String,String> tags) {
        software.amazon.iot.billinggroup.BillingGroupProperties billingGroupProperties =
                software.amazon.iot.billinggroup.BillingGroupProperties.builder().build();
        if (model.getBillingGroupProperties() != null) {
            billingGroupProperties = model.getBillingGroupProperties();
        }
        return CreateBillingGroupRequest.builder()
                .billingGroupName(model.getBillingGroupName())
                .billingGroupProperties(BillingGroupProperties.builder()
                        .billingGroupDescription(billingGroupProperties.getBillingGroupDescription())
                        .build())
                .tags(translateTagsToSdk(tags))
                .build();
    }

    static DescribeBillingGroupRequest translateToReadRequest(final ResourceModel model) {
        return DescribeBillingGroupRequest.builder()
                .billingGroupName(model.getBillingGroupName())
                .build();
    }

    static DeleteBillingGroupRequest translateToDeleteRequest(final ResourceModel model) {
        return DeleteBillingGroupRequest.builder()
                .billingGroupName(model.getBillingGroupName())
                .build();
    }

    static UpdateBillingGroupRequest translateToUpdateRequest(final ResourceModel model) {
        software.amazon.iot.billinggroup.BillingGroupProperties billingGroupProperties =
                software.amazon.iot.billinggroup.BillingGroupProperties.builder().billingGroupDescription("").build();
        if (model.getBillingGroupProperties() != null) {
            billingGroupProperties = model.getBillingGroupProperties();
        }
        return UpdateBillingGroupRequest.builder()
                .billingGroupName(model.getBillingGroupName())
                .billingGroupProperties(BillingGroupProperties.builder()
                        .billingGroupDescription(billingGroupProperties.getBillingGroupDescription())
                        .build())
                .build();
    }

    static ListBillingGroupsRequest translateToListRequest(final String nextToken) {
        return ListBillingGroupsRequest.builder()
                .nextToken(nextToken)
                .build();
    }

    static List<ResourceModel> translateFromListRequest(final ListBillingGroupsResponse listBillingGroupsResponse) {
        return streamOfOrEmpty(listBillingGroupsResponse.billingGroups())
                .map(resource -> ResourceModel.builder()
                        .billingGroupName(resource.groupName())
                        .build())
                .collect(Collectors.toList());
    }

    private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
        return Optional.ofNullable(collection)
                .map(Collection::stream)
                .orElseGet(Stream::empty);
    }

    //Translate tags
    static Set<Tag> translateTagsToSdk(final Map<String, String> tags) {
        if (tags == null) {
            return Collections.emptySet();
        }
        return Optional.of(tags.entrySet()).orElse(Collections.emptySet())
                .stream()
                .map(tag -> Tag.builder()
                        .key(tag.getKey())
                        .value(tag.getValue())
                        .build())
                .collect(Collectors.toSet());
    }

    static Set<software.amazon.iot.billinggroup.Tag> translateTagsFromSdk(final Collection<Tag> tags) {
        return Optional.ofNullable(tags).orElse(Collections.emptySet())
                .stream()
                .map(tag -> software.amazon.iot.billinggroup.Tag.builder()
                        .key(tag.key())
                        .value(tag.value())
                        .build())
                .collect(Collectors.toSet());
    }

    static ListTagsForResourceRequest listResourceTagsRequest(final ResourceModel model) {
        return ListTagsForResourceRequest.builder()
                .resourceArn(model.getArn())
                .build();
    }

    static UntagResourceRequest untagResourceRequest(final String arn, final Set<Tag> tags) {
        return UntagResourceRequest.builder()
                .resourceArn(arn)
                .tagKeys(tags
                        .stream()
                        .map(Tag::key)
                        .collect(Collectors.toSet())
                ).build();
    }

    static TagResourceRequest tagResourceRequest(final String arn, final Collection<Tag> tags) {
        return TagResourceRequest.builder()
                .resourceArn(arn)
                .tags(tags).build();
    }
}
