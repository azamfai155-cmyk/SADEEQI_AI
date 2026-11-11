export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return Response.json({
        status: "ok",
        assistant: "SADEEQI AI",
        version: "1.2.0"
      });
    }

    if (url.pathname === "/") {
      return Response.json({
        name: "SADEEQI AI",
        status: "running"
      });
    }

    return Response.json(
      { error: "Not found" },
      { status: 404 }
    );
  }
};
