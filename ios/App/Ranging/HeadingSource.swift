import Foundation
#if canImport(CoreMotion)
import CoreMotion
import CoreLocation
#endif

/// Emits device heading in degrees (0 = North, clockwise) for CompassFusion. Uses
/// CLLocationManager heading (magnetometer + CoreMotion fusion by the OS). Device-only.
final class HeadingSource: NSObject {
    var onHeading: ((Double) -> Void)?

    #if canImport(CoreMotion)
    private let locationManager = CLLocationManager()

    func start() {
        locationManager.delegate = self
        if CLLocationManager.headingAvailable() {
            locationManager.startUpdatingHeading()
        }
    }

    func stop() { locationManager.stopUpdatingHeading() }
    #endif
}

#if canImport(CoreMotion)
extension HeadingSource: CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        // trueHeading falls back to magneticHeading when true isn't available.
        let heading = newHeading.trueHeading >= 0 ? newHeading.trueHeading : newHeading.magneticHeading
        onHeading?(heading)
    }
}
#endif
