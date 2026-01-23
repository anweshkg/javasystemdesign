package validator

import (
	"fmt"

	"github.com/prometheus/client_golang/prometheus"
	sellerv1 "github.com/swiggy-private/api-registry/gen/proto/go/supply_chain_management_gateway/seller/v1"
	"github.com/swiggy-private/gamma-seller-management/seller-management/internal/pkg/utils"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/fieldmaskpb"
)

// Constant Map to hold all supported fields Mask for which update is allowed
var SellerUpdateSupportedFields = map[string]bool{
	"name":                   true,
	"at_warehouse":           true,
	"state":                  true,
	"third_party_attributes": true,
	"third_party_attributes.vinculum_attributes":           true,
	"third_party_attributes.vinculum_attributes.api_owner": true,
	"third_party_attributes.vinculum_attributes.api_key":   true,
	"third_party_attributes.vinculum_attributes.client_id": true,
	"b2c":                      true,
	"can_be_proc_seller_at_wh": true,
	"can_be_inv_seller_at_wh":  true,
	"can_be_inv_seller_at_pod": true,
	"allowed_email_domains":    true,
}

// Constant Map to hold all required fields for Seller
var SellerRequiredFields = map[string]bool{
	"name":           true,
	"state":          true,
	"pan":            true,
	"pan.pan_number": true,
	"third_party_attributes.vinculum_attributes.api_owner": true,
	"third_party_attributes.vinculum_attributes.api_key":   true,
	"third_party_attributes.vinculum_attributes.client_id": true,
}

var SellerGetMaskAllowedFiekds = map[string]bool{
	"id":                     true,
	"state":                  true,
	"name":                   true,
	"at_warehouse":           true,
	"pan":                    true,
	"pan.pan_number":         true,
	"third_party_attributes": true,
	"third_party_attributes.vinculum_attributes":           true,
	"third_party_attributes.vinculum_attributes.api_owner": true,
	"third_party_attributes.vinculum_attributes.api_key":   true,
	"third_party_attributes.vinculum_attributes.client_id": true,
	"updated_time":             true,
	"updated_by":               true,
	"b2c":                      true,
	"can_be_proc_seller_at_wh": true,
	"can_be_inv_seller_at_wh":  true,
	"can_be_inv_seller_at_pod": true,
	"allowed_email_domains":    true,
}

var SellerAtWarehouseRequiredFields = map[string]bool{
	"third_party_attributes":                               true,
	"third_party_attributes.vinculum_attributes":           true,
	"third_party_attributes.vinculum_attributes.api_owner": true,
	"third_party_attributes.vinculum_attributes.api_key":   true,
	"third_party_attributes.vinculum_attributes.client_id": true,
}

var (
	// Prometheus count for validation failure at overall and seller level
	counterErrSellerReqValidation = prometheus.NewCounterVec(prometheus.CounterOpts{
		Name: "seller_api_validation_error",
		Help: "Number of errors in seller api",
	}, []string{"error_level", "error_code", "error_field"})
)

func init() {
	prometheus.MustRegister(counterErrSellerReqValidation)
}

// ValidateCreateSellerRequest is the interface for validating the seller request. It contains methods for validating the seller request.
// The methods are used to validate the seller request and return the valid sellers and failed sellers.
func ValidateCreateSellerRequest(request *sellerv1.BatchCreateSellersRequest) ([]*sellerv1.Seller, []*sellerv1.BatchCreateSellersResponse_FailedCreate, error) {
	// Validate the seller request
	if len(request.GetRequests()) == 0 {
		counterErrSellerReqValidation.WithLabelValues("BatchCreateSellersRequest", "invalid_request", "empty_seller_request").Inc()
		return nil, nil, status.Error(codes.InvalidArgument, "no seller requests found")
	}
	res, field := utils.CheckField(request, map[string]bool{"request_context": true, "mp_context": true, "requests": true, "actor": true})
	if !res {
		counterErrSellerReqValidation.WithLabelValues("BatchCreateSellersRequest", "invalid_request", field).Inc()
		return nil, nil, status.Error(codes.InvalidArgument, fmt.Sprintf("required fields are missing, %s", field))
	}

	if len(request.GetRequests()) > 100 {
		counterErrSellerReqValidation.WithLabelValues("BatchCreateSellersRequest", "invalid_request", "too_many_sellers").Inc()
		return nil, nil, status.Error(codes.InvalidArgument, "too many sellers in request")
	}
	validSellers := make([]*sellerv1.Seller, 0)
	failedSeller := make([]*sellerv1.BatchCreateSellersResponse_FailedCreate, 0)
	for _, sr := range request.GetRequests() {
		if sr == nil {
			counterErrSellerReqValidation.WithLabelValues("BatchCreateSellersRequest", "invalid_request", "create_seller_nil").Inc()
			failedSeller = append(failedSeller, &sellerv1.BatchCreateSellersResponse_FailedCreate{
				Seller: nil,
				Status: status.New(codes.InvalidArgument, "create seller request is nil").Proto(),
			})
			continue
		}
		res, field = utils.CheckField(sr, map[string]bool{"seller": true})
		if !res {
			counterErrSellerReqValidation.WithLabelValues("BatchCreateSellersRequest", "invalid_request", field).Inc()
			failedSeller = append(failedSeller, &sellerv1.BatchCreateSellersResponse_FailedCreate{
				Seller: sr.GetSeller(),
				Status: status.New(codes.InvalidArgument, fmt.Sprintf("required fields are missing, %s", field)).Proto(),
			})
			continue
		}
		if sr.GetSeller().GetState() == sellerv1.Seller_STATE_INVALID {
			counterErrSellerReqValidation.WithLabelValues("BatchCreateSellersRequest", "invalid_request", "invalid_state").Inc()
			failedSeller = append(failedSeller, &sellerv1.BatchCreateSellersResponse_FailedCreate{
				Seller: sr.GetSeller(),
				Status: status.New(codes.InvalidArgument, "invalid state").Proto(),
			})
			continue
		}
		res, field = IsRequiredFieldPresent(sr.GetSeller())
		if !res {
			counterErrSellerReqValidation.WithLabelValues("BatchCreateSellersRequest", "invalid_request", field).Inc()
			failedSeller = append(failedSeller, &sellerv1.BatchCreateSellersResponse_FailedCreate{
				Seller: sr.GetSeller(),
				Status: status.New(codes.InvalidArgument, fmt.Sprintf("required fields are missing, %s", field)).Proto(),
			})
			continue
		}

		// Perform custom seller business logic validations
		if err := ValidateSeller(sr.GetSeller()); err != nil {
			counterErrSellerReqValidation.WithLabelValues("BatchCreateSellersRequest", "validation_failed", "business_logic").Inc()
			failedSeller = append(failedSeller, &sellerv1.BatchCreateSellersResponse_FailedCreate{
				Seller: sr.GetSeller(),
				Status: status.Convert(err).Proto(),
			})
			continue
		}

		validSellers = append(validSellers, sr.GetSeller())
	}
	return validSellers, failedSeller, nil
}

func ValidateUpdateSellerRequest(request *sellerv1.BatchUpdateSellersRequest) ([]*sellerv1.BatchUpdateSellersRequest_UpdateSellerRequest, []*sellerv1.BatchUpdateSellersResponse_FailedUpdate, error) {
	if len(request.GetRequests()) == 0 {
		counterErrSellerReqValidation.WithLabelValues("BatchUpdateSellersRequest", "invalid_request", "empty_seller_request").Inc()
		return nil, nil, status.Error(codes.InvalidArgument, "no seller requests found")
	}
	res, field := utils.CheckField(request, map[string]bool{"request_context": true, "mp_context": true, "requests": true, "actor": true})
	if !res {
		counterErrSellerReqValidation.WithLabelValues("BatchUpdateSellersRequest", "invalid_request", field).Inc()
		return nil, nil, status.Error(codes.InvalidArgument, fmt.Sprintf("required fields are missing, %s", field))
	}

	validSellers := make([]*sellerv1.BatchUpdateSellersRequest_UpdateSellerRequest, 0)
	invalidSellers := make([]*sellerv1.BatchUpdateSellersResponse_FailedUpdate, 0)
	for _, sr := range request.GetRequests() {
		if sr == nil {
			counterErrSellerReqValidation.WithLabelValues("BatchUpdateSellersRequest", "invalid_request", "update_seller_nil").Inc()
			invalidSellers = append(invalidSellers, &sellerv1.BatchUpdateSellersResponse_FailedUpdate{
				Seller: nil,
				Status: status.New(codes.InvalidArgument, "update seller is nil").Proto(),
			})
			continue
		}
		res, field = utils.CheckField(sr, map[string]bool{"seller": true, "update_mask": true})
		if !res {
			counterErrSellerReqValidation.WithLabelValues("BatchUpdateSellersRequest", "invalid_request", field).Inc()
			invalidSellers = append(invalidSellers, &sellerv1.BatchUpdateSellersResponse_FailedUpdate{
				Seller: sr.GetSeller(),
				Status: status.New(codes.InvalidArgument, fmt.Sprintf("required fields are missing, %s", field)).Proto(),
			})
			continue
		}
		if len(sr.GetUpdateMask().GetPaths()) == 0 {
			counterErrSellerReqValidation.WithLabelValues("BatchUpdateSellersRequest", "invalid_request", "empty_update_mask").Inc()
			invalidSellers = append(invalidSellers, &sellerv1.BatchUpdateSellersResponse_FailedUpdate{
				Seller: sr.GetSeller(),
				Status: status.New(codes.InvalidArgument, fmt.Sprintf("invalid update field mask, empty mask")).Proto(),
			})
			continue
		}
		res, field = ValidateUpdateMask(sr.GetUpdateMask())
		if !res {
			counterErrSellerReqValidation.WithLabelValues("BatchUpdateSellersRequest", "FieldMask", "invalid_update_field_mask_path").Inc()
			invalidSellers = append(invalidSellers, &sellerv1.BatchUpdateSellersResponse_FailedUpdate{
				Seller: sr.GetSeller(),
				Status: status.New(codes.InvalidArgument, fmt.Sprintf("invalid update field mask path, %s", field)).Proto(),
			})
			continue
		}
		res, field = utils.CheckField(sr.GetSeller(), map[string]bool{"id": true})
		if !res {
			counterErrSellerReqValidation.WithLabelValues("BatchUpdateSellersRequest", "invalid_request", field).Inc()
			invalidSellers = append(invalidSellers, &sellerv1.BatchUpdateSellersResponse_FailedUpdate{
				Seller: sr.GetSeller(),
				Status: status.New(codes.InvalidArgument, fmt.Sprintf("required fields are missing, %s", field)).Proto(),
			})
			continue
		}
		res, field = utils.CheckFieldList(sr.GetSeller(), sr.GetUpdateMask().GetPaths())
		if !res {
			counterErrSellerReqValidation.WithLabelValues("BatchUpdateSellersRequest", "invalid_request", field).Inc()
			invalidSellers = append(invalidSellers, &sellerv1.BatchUpdateSellersResponse_FailedUpdate{
				Seller: sr.GetSeller(),
				Status: status.New(codes.InvalidArgument, fmt.Sprintf("required fields are missing, %s", field)).Proto(),
			})
			continue
		}

		validSellers = append(validSellers,
			&sellerv1.BatchUpdateSellersRequest_UpdateSellerRequest{Seller: sr.GetSeller(), UpdateMask: sr.GetUpdateMask()})
	}
	return validSellers, invalidSellers, nil

}

// ValidateUpdateMask validates the update mask for seller. It checks if the update mask is valid or not and update mask to
// be supported fields only.
func ValidateUpdateMask(updateMask *fieldmaskpb.FieldMask) (bool, string) {
	for _, path := range updateMask.GetPaths() {
		if !SellerUpdateSupportedFields[path] {
			counterErrSellerReqValidation.WithLabelValues("BatchUpdateSellersRequest", "FieldMask", "invalid_update_field_mask_path").Inc()
			return false, path
		}
	}
	return true, ""
}

// ValidateBatchGetSellerRequest validates the get seller request. It checks if the request is valid or not and returns the valid
// seller ids and failed seller ids.
func ValidateBatchGetSellerRequest(request *sellerv1.BatchGetSellersRequest) error {
	res, field := utils.CheckField(request, map[string]bool{"request_context": true, "mp_context": true, "ids": true})
	if !res {
		counterErrSellerReqValidation.WithLabelValues("BatchGetSellersRequest", "invalid_request", field).Inc()
		return status.Error(codes.InvalidArgument, fmt.Sprintf("required fields are missing, %s", field))
	}
	if request.GetGetMask() != nil && len(request.GetGetMask().GetPaths()) == 0 {
		counterErrSellerReqValidation.WithLabelValues("BatchGetSellersRequest", "invalid_request", "empty_get_mask").Inc()
		return status.Error(codes.InvalidArgument, "get mask is empty")
	}
	for _, path := range request.GetGetMask().GetPaths() {
		if !SellerGetMaskAllowedFiekds[path] {
			counterErrSellerReqValidation.WithLabelValues("BatchGetSellersRequest", "FieldMask", "invalid_get_field_mask_path").Inc()
			return status.Error(codes.InvalidArgument, fmt.Sprintf("invalid get path field mask, %s", path))
		}
	}
	return nil
}

// IsRequiredFieldPresent checks if the required fields are present in the seller request or not. It returns true if the required fields
// are present else it returns false. It also returns the field which is missing.
func IsRequiredFieldPresent(seller *sellerv1.Seller) (bool, string) {
	res, field := utils.CheckField(seller, SellerRequiredFields)
	if !res {
		return res, field
	}
	if seller.GetAtWarehouse() {
		return utils.CheckField(seller, SellerAtWarehouseRequiredFields)
	}
	return true, ""
}

// ValidateSeller performs custom business logic validations for seller data
func ValidateSeller(seller *sellerv1.Seller) error {
	// Validate warehouse-specific flags: can_be_proc_seller_at_wh and can_be_inv_seller_at_wh
	// can only be true when at_warehouse is true
	if !seller.GetAtWarehouse() && (seller.GetCanBeProcSellerAtWh() || seller.GetCanBeInvSellerAtWh()) {
		counterErrSellerReqValidation.WithLabelValues("SellerValidation", "invalid_warehouse_flags", "warehouse_flags_without_warehouse").Inc()
		return status.Error(codes.InvalidArgument, "can_be_proc_seller_at_wh and can_be_inv_seller_at_wh can only be true when at_warehouse is true")
	}

	return nil
}

func ValidateListSellerRequest(request *sellerv1.ListSellersRequest) error {
	res, field := utils.CheckField(request, map[string]bool{"request_context": true, "mp_context": true, "pagination": true})
	if !res {
		counterErrSellerReqValidation.WithLabelValues("ListSellersRequest", "invalid_request", field).Inc()
		return status.Error(codes.InvalidArgument, fmt.Sprintf("required fields are missing, %s", field))
	}
	return nil
}
