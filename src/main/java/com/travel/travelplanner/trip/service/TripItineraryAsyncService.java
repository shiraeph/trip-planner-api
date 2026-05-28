package com.travel.travelplanner.trip.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.travel.travelplanner.ai.dto.BilingualItinerary;
import com.travel.travelplanner.trip.api.RegenerateTripRequest;
import com.travel.travelplanner.trip.domain.TripPlan;
import com.travel.travelplanner.trip.domain.enums.TripStatus;
import com.travel.travelplanner.trip.domain.itinerary.BlockPlan;
import com.travel.travelplanner.trip.domain.itinerary.DayPlan;
import com.travel.travelplanner.trip.domain.itinerary.Itinerary;
import com.travel.travelplanner.trip.repository.TripPlanRepository;
import com.travel.travelplanner.trip.service.generator.GptTripItineraryGenerator;
import com.travel.travelplanner.trip.service.validation.ItineraryNormalizer;
import com.travel.travelplanner.trip.service.validation.ItineraryValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripItineraryAsyncService {
    private static final Logger log = LoggerFactory.getLogger(TripItineraryAsyncService.class);
    private static final int MAX_GENERATION_ATTEMPTS = 2;
    private static final String FRIENDLY_ERROR_MESSAGE = "Oh no! Something went wrong. Please try again.";

    private final ItineraryNormalizer itineraryNormalizer;
    private final ItineraryValidator itineraryValidator;
    private final TripPlanRepository tripPlanRepository;
    private final GptTripItineraryGenerator gptTripItineraryGenerator;

    @Async
    public void generateItineraryAsync(String tripPlanId) {
        TripPlan plan = tripPlanRepository.findById(tripPlanId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            try {
                BilingualItinerary bilingual = normalize(plan, gptTripItineraryGenerator.generate(plan));
                List<String> validation = itineraryValidator.validate(plan, bilingual.getEn());
                if (validation.isEmpty()) {
                    plan.setItineraryEn(bilingual.getEn());
                    plan.setItineraryHe(bilingual.getHe());
                    plan.setTripStatus(TripStatus.READY);
                    plan.setErrorMessage(null);
                    tripPlanRepository.save(plan);
                    return;
                }

                log.warn("Trip {} attempt {} validation failed ({} issues): {}", tripPlanId, attempt, validation.size(), validation);

                BilingualItinerary fix = normalize(plan, gptTripItineraryGenerator.generateFix(plan, validation));
                List<String> secondValidation = itineraryValidator.validate(plan, fix.getEn());
                if (secondValidation.isEmpty()) {
                    plan.setItineraryEn(fix.getEn());
                    plan.setItineraryHe(fix.getHe());
                    plan.setTripStatus(TripStatus.READY);
                    plan.setErrorMessage(null);
                    tripPlanRepository.save(plan);
                    return;
                }

                log.warn("Trip {} attempt {} second validation failed ({} issues): {}", tripPlanId, attempt, secondValidation.size(), secondValidation);

                BilingualItinerary fix2 = normalize(plan, gptTripItineraryGenerator.generateFix(plan, secondValidation));
                List<String> thirdValidation = itineraryValidator.validate(plan, fix2.getEn());
                if (thirdValidation.isEmpty()) {
                    plan.setItineraryEn(fix2.getEn());
                    plan.setItineraryHe(fix2.getHe());
                    plan.setTripStatus(TripStatus.READY);
                    plan.setErrorMessage(null);
                    tripPlanRepository.save(plan);
                    return;
                }

                log.warn("Trip {} attempt {} third validation failed ({} issues): {}", tripPlanId, attempt, thirdValidation.size(), thirdValidation);

                if (attempt < MAX_GENERATION_ATTEMPTS) {
                    continue;
                }

                plan.setTripStatus(TripStatus.FAILED);
                plan.setErrorMessage(FRIENDLY_ERROR_MESSAGE);
                tripPlanRepository.save(plan);
                return;
            } catch (Exception e) {
                log.error("Trip {} generation attempt {} failed", tripPlanId, attempt, e);
                if (attempt < MAX_GENERATION_ATTEMPTS) {
                    continue;
                }
                plan.setTripStatus(TripStatus.FAILED);
                plan.setErrorMessage(FRIENDLY_ERROR_MESSAGE);
                tripPlanRepository.save(plan);
                return;
            }
        }
    }

    @Async
    public void generateItineraryAsync(
            String tripPlanId,
            Itinerary oldEn,
            Itinerary oldHe,
            RegenerateTripRequest request) {
        TripPlan plan = tripPlanRepository.findById(tripPlanId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        List<RegenerateTripRequest.LockedBlock> lockedBlocks = request != null ? request.getLockedBlocks() : null;
        List<RegenerateTripRequest.LockedItem> lockedItems = request != null ? request.getLockedItems() : null;

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            try {
                BilingualItinerary bilingual = normalize(plan, gptTripItineraryGenerator.generate(plan));
                Itinerary mergedEn = mergeLockedItems(oldEn, mergeLockedBlocks(oldEn, bilingual.getEn(), lockedBlocks), lockedItems);
                Itinerary mergedHe = mergeLockedItems(oldHe, mergeLockedBlocks(oldHe, bilingual.getHe(), lockedBlocks), lockedItems);
                itineraryNormalizer.normalize(plan, mergedEn, false);
                itineraryNormalizer.normalize(plan, mergedHe, true);

                List<String> validation = itineraryValidator.validate(plan, mergedEn);
                if (validation.isEmpty()) {
                    plan.setItineraryEn(mergedEn);
                    plan.setItineraryHe(mergedHe);
                    plan.setTripStatus(TripStatus.READY);
                    plan.setErrorMessage(null);
                    tripPlanRepository.save(plan);
                    return;
                }

                log.warn("Trip {} regenerate attempt {} validation failed ({} issues): {}", tripPlanId, attempt, validation.size(), validation);

                BilingualItinerary fix = normalize(plan, gptTripItineraryGenerator.generateFix(plan, validation));
                Itinerary mergedFixEn = mergeLockedItems(oldEn, mergeLockedBlocks(oldEn, fix.getEn(), lockedBlocks), lockedItems);
                Itinerary mergedFixHe = mergeLockedItems(oldHe, mergeLockedBlocks(oldHe, fix.getHe(), lockedBlocks), lockedItems);
                itineraryNormalizer.normalize(plan, mergedFixEn, false);
                itineraryNormalizer.normalize(plan, mergedFixHe, true);

                List<String> secondValidation = itineraryValidator.validate(plan, mergedFixEn);
                if (secondValidation.isEmpty()) {
                    plan.setItineraryEn(mergedFixEn);
                    plan.setItineraryHe(mergedFixHe);
                    plan.setTripStatus(TripStatus.READY);
                    plan.setErrorMessage(null);
                    tripPlanRepository.save(plan);
                    return;
                }

                log.warn("Trip {} regenerate attempt {} second validation failed ({} issues): {}", tripPlanId, attempt, secondValidation.size(), secondValidation);

                BilingualItinerary fix2 = normalize(plan, gptTripItineraryGenerator.generateFix(plan, secondValidation));
                Itinerary mergedFix2En = mergeLockedItems(oldEn, mergeLockedBlocks(oldEn, fix2.getEn(), lockedBlocks), lockedItems);
                Itinerary mergedFix2He = mergeLockedItems(oldHe, mergeLockedBlocks(oldHe, fix2.getHe(), lockedBlocks), lockedItems);
                itineraryNormalizer.normalize(plan, mergedFix2En, false);
                itineraryNormalizer.normalize(plan, mergedFix2He, true);

                List<String> thirdValidation = itineraryValidator.validate(plan, mergedFix2En);
                if (thirdValidation.isEmpty()) {
                    plan.setItineraryEn(mergedFix2En);
                    plan.setItineraryHe(mergedFix2He);
                    plan.setTripStatus(TripStatus.READY);
                    plan.setErrorMessage(null);
                    tripPlanRepository.save(plan);
                    return;
                }

                log.warn("Trip {} regenerate attempt {} third validation failed ({} issues): {}", tripPlanId, attempt, thirdValidation.size(), thirdValidation);

                if (attempt < MAX_GENERATION_ATTEMPTS) {
                    continue;
                }

                plan.setTripStatus(TripStatus.FAILED);
                plan.setErrorMessage(FRIENDLY_ERROR_MESSAGE);
                tripPlanRepository.save(plan);
                return;
            } catch (Exception e) {
                log.error("Trip {} regenerate attempt {} failed", tripPlanId, attempt, e);
                if (attempt < MAX_GENERATION_ATTEMPTS) {
                    continue;
                }
                plan.setTripStatus(TripStatus.FAILED);
                plan.setErrorMessage(FRIENDLY_ERROR_MESSAGE);
                tripPlanRepository.save(plan);
                return;
            }
        }
    }

    private static Itinerary mergeLockedBlocks(
            Itinerary oldItinerary,
            Itinerary newItinerary,
            List<RegenerateTripRequest.LockedBlock> lockedBlocks) {
        if (newItinerary == null) return null;
        if (oldItinerary == null || lockedBlocks == null || lockedBlocks.isEmpty()) return newItinerary;

        List<DayPlan> newDays = newItinerary.getDayPlans();
        List<DayPlan> oldDays = oldItinerary.getDayPlans();
        if (newDays == null || oldDays == null) return newItinerary;

        for (RegenerateTripRequest.LockedBlock lb : lockedBlocks) {
            if (lb == null || lb.getDay() == null || lb.getTimeBlock() == null) continue;
            int dayIdx = lb.getDay() - 1;
            if (dayIdx < 0 || dayIdx >= newDays.size() || dayIdx >= oldDays.size()) continue;

            DayPlan newDay = newDays.get(dayIdx);
            DayPlan oldDay = oldDays.get(dayIdx);
            if (newDay == null || oldDay == null) continue;

            BlockPlan oldBlock = findBlock(oldDay.getBlocks(), lb.getTimeBlock());
            if (oldBlock == null) continue;

            List<BlockPlan> newBlocks = newDay.getBlocks();
            if (newBlocks == null) continue;

            for (int i = 0; i < newBlocks.size(); i++) {
                BlockPlan nb = newBlocks.get(i);
                if (nb != null && lb.getTimeBlock().equals(nb.getTimeBlock())) {
                    newBlocks.set(i, deepCopyBlock(oldBlock));
                }
            }
        }

        return newItinerary;
    }

    private static Itinerary mergeLockedItems(
            Itinerary oldItinerary,
            Itinerary newItinerary,
            List<RegenerateTripRequest.LockedItem> lockedItems) {
        if (newItinerary == null) return null;
        if (oldItinerary == null || lockedItems == null || lockedItems.isEmpty()) return newItinerary;

        List<DayPlan> newDays = newItinerary.getDayPlans();
        List<DayPlan> oldDays = oldItinerary.getDayPlans();
        if (newDays == null || oldDays == null) return newItinerary;

        for (RegenerateTripRequest.LockedItem li : lockedItems) {
            if (li == null || li.getDay() == null || li.getTimeBlock() == null || li.getItemIndex() == null) continue;
            int dayIdx = li.getDay() - 1;
            if (dayIdx < 0 || dayIdx >= newDays.size() || dayIdx >= oldDays.size()) continue;

            DayPlan newDay = newDays.get(dayIdx);
            DayPlan oldDay = oldDays.get(dayIdx);
            if (newDay == null || oldDay == null) continue;

            BlockPlan oldBlock = findBlock(oldDay.getBlocks(), li.getTimeBlock());
            BlockPlan newBlock = findBlock(newDay.getBlocks(), li.getTimeBlock());
            if (oldBlock == null || newBlock == null) continue;
            if (oldBlock.getItems() == null || newBlock.getItems() == null) continue;

            int idx = li.getItemIndex();
            if (idx < 0 || idx >= oldBlock.getItems().size() || idx >= newBlock.getItems().size()) continue;

            newBlock.getItems().set(idx, oldBlock.getItems().get(idx));
        }

        return newItinerary;
    }

    private static BlockPlan findBlock(List<BlockPlan> blocks, com.travel.travelplanner.trip.domain.enums.TimeBlock timeBlock) {
        if (blocks == null) return null;
        for (BlockPlan b : blocks) {
            if (b != null && timeBlock.equals(b.getTimeBlock())) return b;
        }
        return null;
    }

    private static BlockPlan deepCopyBlock(BlockPlan b) {
        BlockPlan copy = new BlockPlan();
        copy.setTimeBlock(b.getTimeBlock());
        if (b.getItems() != null) {
            copy.setItems(new ArrayList<>(b.getItems()));
        }
        return copy;
    }

    private BilingualItinerary normalize(TripPlan plan, BilingualItinerary bilingual) {
        if (bilingual == null) {
            return null;
        }
        if (bilingual.getEn() != null) {
            itineraryNormalizer.normalize(plan, bilingual.getEn(), false);
        }
        if (bilingual.getHe() != null) {
            itineraryNormalizer.normalize(plan, bilingual.getHe(), true);
        }
        return bilingual;
    }
}

