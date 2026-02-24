<script>
    let message = "";

    async function fetchMessage() {
        const res = await fetch("http://localhost:8080/api");
        message = await res.json().then(data => data.message);
    }

    fetchMessage();
</script>

<p>{message}</p>
<p>Here stuff is displayed. Someday maybe a map :skull:</p>