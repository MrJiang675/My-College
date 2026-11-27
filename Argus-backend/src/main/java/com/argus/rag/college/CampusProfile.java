package com.argus.rag.college;

import jakarta.validation.constraints.Size;

public record CampusProfile(
        @Size(max=120) String school,
        @Size(max=120) String college,
        @Size(max=120) String major,
        @Size(max=40) String grade) {}
