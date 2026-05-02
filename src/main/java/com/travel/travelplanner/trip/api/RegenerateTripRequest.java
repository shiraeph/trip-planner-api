package com.travel.travelplanner.trip.api;

import java.util.List;

import com.travel.travelplanner.trip.domain.enums.TimeBlock;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegenerateTripRequest {
    private List<LockedBlock> lockedBlocks;
    private List<LockedItem> lockedItems;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LockedBlock {
        private Integer day; // 1-based day number
        private TimeBlock timeBlock;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LockedItem {
        private Integer day; // 1-based day number
        private TimeBlock timeBlock;
        private Integer itemIndex; // 0-based index within the block
    }
}

