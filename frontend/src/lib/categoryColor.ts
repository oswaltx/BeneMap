const categoryPalette = [
    { bg: "#FDEBB0", text: "#6B4E00" },
    { bg: "#CFE3D2", text: "#1F4A2C" },
    { bg: "#FBD8CC", text: "#8A3B22" },
    { bg: "#D7E4F0", text: "#204A6B" },
    { bg: "#E8DFF5", text: "#4A2E6B" },
];

export function categoryColor(category: string) {
    let hash = 0;
    for (let i = 0; i < category.length; i++) {
        hash = category.charCodeAt(i) + ((hash << 5) - hash);
    }
    const index = Math.abs(hash) % categoryPalette.length;
    return categoryPalette[index];
}
