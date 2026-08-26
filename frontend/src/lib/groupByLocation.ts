export interface LocationGroup<T> {
    key: string;
    lat: number;
    lng: number;
    members: T[];
}

export function groupByLocation<T extends { lat: number; lng: number; dateTime: string | null }>(
    markers: T[]
): LocationGroup<T>[] {
    const groups = new Map<string, LocationGroup<T>>();

    for (const marker of markers) {
        const key = `${marker.lat.toFixed(5)},${marker.lng.toFixed(5)}`;
        let group = groups.get(key);
        if (!group) {
            group = { key, lat: marker.lat, lng: marker.lng, members: [] };
            groups.set(key, group);
        }
        group.members.push(marker);
    }

    for (const group of groups.values()) {
        group.members.sort((a, b) => {
            if (!a.dateTime) return 1;
            if (!b.dateTime) return -1;
            return a.dateTime.localeCompare(b.dateTime);
        });
    }

    return [...groups.values()];
}
