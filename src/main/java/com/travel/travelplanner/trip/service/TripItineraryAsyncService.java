package com.travel.travelplanner.trip.service;

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
import com.travel.travelplanner.trip.service.generator.ItineraryGenerationListener;
import com.travel.travelplanner.trip.service.validation.ItineraryNormalizer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripItineraryAsyncService {
    private static final Logger log = LoggerFactory.getLogger(TripItineraryAsyncService.class);
    private static final int MAX_GENERATION_ATTEMPTS = 2;
    private static final String FRIENDLY_ERROR_MESSAGE = "Oh no! Something went wrong. Please try again.";

    private final ItineraryNormalizer itineraryNormalizer;
    private final TripPlanRepository tripPlanRepository;
    private final GptTripItineraryGenerator gptTripItineraryGenerator;

    @Async
    public void generateItineraryAsync(String tripPlanId) {
        TripPlan plan = tripPlanRepository.findById(tripPlanId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            try {
                if (tryGenerateAndSave(plan, tripPlanId, attempt, null, null, null, null)) {
                    return;
                }
                if (attempt < MAX_GENERATION_ATTEMPTS) {
                    continue;
                }
                markFailed(plan);
                return;
            } catch (Exception e) {
                log.error("Trip {} generation attempt {} failed", tripPlanId, attempt, e);
                if (attempt < MAX_GENERATION_ATTEMPTS) {
                    continue;
                }
                markFailed(plan);
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
                if (tryGenerateAndSave(plan, tripPlanId, attempt, oldEn, oldHe, lockedBlocks, lockedItems)) {
                    return;
                }
                if (attempt < MAX_GENERATION_ATTEMPTS) {
                    continue;
                }
                markFailed(plan);
                return;
            } catch (Exception e) {
                log.error("Trip {} regenerate attempt {} failed", tripPlanId, attempt, e);
                if (attempt < MAX_GENERATION_ATTEMPTS) {
                    continue;
                }
                markFailed(plan);
                return;
            }
        }
    }

    /** Generate once and save READY. Returns true on success. */
    private boolean tryGenerateAndSave(
            TripPlan plan,
            String tripPlanId,
            int attempt,
            Itinerary oldEn,
            Itinerary oldHe,
            List<RegenerateTripRequest.LockedBlock> lockedBlocks,
            List<RegenerateTripRequest.LockedItem> lockedItems) {
        resetPartialItinerary(plan);
        ItineraryGenerationListener listener = createProgressListener(plan);

        BilingualItinerary bilingual = normalize(plan, gptTripItineraryGenerator.generate(plan, listener));
        Itinerary candidateEn = applyLocks(oldEn, bilingual.getEn(), lockedBlocks, lockedItems);
        Itinerary candidateHe = applyLocks(oldHe, bilingual.getHe(), lockedBlocks, lockedItems);
        normalizeMerged(plan, candidateEn, candidateHe);
        saveReady(plan, candidateEn, candidateHe);
        return true;
    }

    private Itinerary applyLocks(
            Itinerary oldItinerary,
            Itinerary generated,
            List<RegenerateTripRequest.LockedBlock> lockedBlocks,
            List<RegenerateTripRequest.LockedItem> lockedItems) {
        if (oldItinerary == null || lockedBlocks == null && lockedItems == null) {
            return generated;
        }
        Itinerary merged = mergeLockedItems(oldItinerary, mergeLockedBlocks(oldItinerary, generated, lockedBlocks), lockedItems);
        return merged != null ? merged : generated;
    }

    private void normalizeMerged(TripPlan plan, Itinerary en, Itinerary he) {
        if (en != null) {
            itineraryNormalizer.normalize(plan, en, false);
        }
        if (he != null) {
            itineraryNormalizer.normalize(plan, he, true);
        }
    }

    private void resetPartialItinerary(TripPlan plan) {
        plan.setItineraryEn(null);
        plan.setItineraryHe(null);
        plan.setGenerationProgress(null);
        plan.setTripStatus(TripStatus.GENERATING);
        plan.setErrorMessage(null);
        tripPlanRepository.save(plan);
    }

    private ItineraryGenerationListener createProgressListener(TripPlan plan) {
        return (progress, partialEn, partialHe) -> {
            if (partialEn != null) {
                plan.setItineraryEn(partialEn);
            }
            if (partialHe != null) {
                plan.setItineraryHe(partialHe);
            }
            plan.setGenerationProgress(progress);
            plan.setTripStatus(TripStatus.GENERATING);
            tripPlanRepository.save(plan);
        };
    }

    private void saveReady(TripPlan plan, Itinerary en, Itinerary he) {
        plan.setItineraryEn(en);
        plan.setItineraryHe(he);
        plan.setTripStatus(TripStatus.READY);
        plan.setErrorMessage(null);
        plan.setGenerationProgress(null);
        tripPlanRepository.save(plan);
    }

    private void markFailed(TripPlan plan) {
        plan.setTripStatus(TripStatus.FAILED);
        plan.setErrorMessage(FRIENDLY_ERROR_MESSAGE);
        plan.setGenerationProgress(null);
        tripPlanRepository.save(plan);
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
            copy.setItems(new java.util.ArrayList<>(b.getItems()));
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
