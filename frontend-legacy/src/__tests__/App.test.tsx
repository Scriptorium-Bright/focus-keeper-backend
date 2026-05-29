import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { App } from "../App";

describe("App", () => {
  afterEach(() => {
    window.localStorage.clear();
  });

  it("saves validation context and moves to the plan stage", async () => {
    render(<App />);

    await userEvent.clear(screen.getByLabelText("userId"));
    await userEvent.type(screen.getByLabelText("userId"), "flow-user");
    await userEvent.clear(screen.getByLabelText("metricDate"));
    await userEvent.type(screen.getByLabelText("metricDate"), "2026-05-13");
    await userEvent.click(screen.getByRole("button", { name: "컨텍스트 저장" }));

    expect(screen.getByText("검증 컨텍스트를 저장했습니다.")).toBeInTheDocument();
    expect(screen.getByText("복귀를 위한 최소 계획만 남기고 바로 실행 가능한 상태로 만듭니다.")).toBeInTheDocument();
  });
});
